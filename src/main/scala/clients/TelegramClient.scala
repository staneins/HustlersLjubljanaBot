package clients

import cats.effect.IO
import cats.syntax.all._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import config.Config
import config.Config.ConfigOps
import dto.{GetUpdatesResponse, TelegramMessage, Update}
import model.Message
import repository.MessageRepository

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import scala.concurrent.duration._

/**
 * Клиент Telegram Bot API.
 *
 * Отвечает за:
 * - Long Polling: постоянное получение новых сообщений из чата
 * - Сохранение каждого сообщения в MongoDB через репозиторий
 * - Отправку сообщений обратно в чат (саммари, приветствие и т.д.)
 *
 * Зависимости передаются через конструктор (constructor injection).
 * Все side-effect'ы (сеть, время) обёрнуты в IO.
 *
 * Использует JDK HttpClient (не Akka HTTP, не sttp) для минимума зависимостей.
 */
class TelegramClient(config: Config, repo: MessageRepository[IO]) {

  // JDK HttpClient — встроен в Java 11+, не требует сторонних библиотек
  private val httpClient: HttpClient = HttpClient.newHttpClient()

  // Jackson для парсинга JSON-ответов от Telegram API.
  // DefaultScalaModule добавляет поддержку Scala-типов (case class, Option, Seq)
  private val objectMapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)

  // Флаг работы listener'а. @volatile — виден из других потоков без кэширования
  @volatile private var running: Boolean = true

  // Offset для Long Polling — Telegram отдаёт только обновления с update_id >= offset.
  // Инкрементируем после каждого батча, чтобы не получить одно и то же дважды
  @volatile private var offset: Int = 0

  /**
   * Запустить Long Polling для получения обновлений.
   *
   * Long Polling работает так:
   * 1. Отправляем GET /getUpdates?offset=X&timeout=30
   * 2. Telegram ждёт до 30 секунд, пока появится новое сообщение
   * 3. Получаем список Update'ов (каждый содержит message)
   * 4. Фильтруем по targetChatId, конвертируем в доменную модель, сохраняем в MongoDB
   * 5. Обновляем offset, чтобы не получить те же сообщения снова
   * 6. Повторяем
   */
  def startListening(): IO[Unit] = {

    /**
     * Обработать одно обновление (Update) от Telegram.
     *
     * Логика:
     * - Если сообщение с текстом и из нашего чата → сохраняем в MongoDB
     * - Если сообщение из другого чата → игнорируем
     * - Если это не сообщение (callback, команда боту и т.д.) → игнорируем
     */
    def processUpdate(update: Update): IO[Unit] = {
      update.message match {
        case Some(msg: TelegramMessage) if msg.text.isDefined && msg.chat.id == config.targetChatId =>
          Message.fromTelegramMessage(msg) match {
            case Some(domainMessage) =>
              repo.create(domainMessage) >>
                IO.println(s"[${msg.from.map(_.firstName).getOrElse("Unknown")}] ${msg.text.get}")
            case None =>
              IO.unit
          }
        case Some(msg) if msg.chat.id != config.targetChatId =>
          IO.unit // Игнорируем сообщения из других чатов
        case _ => IO.unit
      }
    }

    /**
     * Сделать один HTTP-запрос к Telegram getUpdates.
     *
     * timeout=30 — сервер Telegram будет ждать до 30 секунд, прежде чем
     * вернёт пустой ответ. Это снижает нагрузку на API (не спамим запросами).
     */
    def fetchUpdates: IO[GetUpdatesResponse] = IO {
      val url = s"${config.apiUrl}/bot${config.botToken}/getUpdates?offset=$offset&timeout=30"
      val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build()

      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      val json = response.body()

      if (response.statusCode() != 200) {
        throw new RuntimeException(s"Telegram API error: ${response.statusCode()} - $json")
      }

      objectMapper.readValue(json, classOf[GetUpdatesResponse])
    }

    /**
     * Основной цикл Long Polling.
     *
     * Пауза 1 секунда между запросами — чтобы не спамить Telegram API
     * при ошибках или пустых ответах.
     */
    def loop: IO[Unit] = {
      for {
        _ <- IO.sleep(1.second)
        response <- fetchUpdates.handleErrorWith { err =>
          IO.println(s"Ошибка при получении обновлений: $err") >> IO.pure(GetUpdatesResponse(false, Nil))
        }

        _ <- if (response.ok && response.updates.nonEmpty) {
          val lastOffset = response.updates.map(_.updateId).max
          IO { offset = lastOffset + 1 } >>
            response.updates.filter(_.message.isDefined).traverse(processUpdate)
        } else {
          IO.unit
        }

        _ <- if (running) loop else IO.unit
      } yield ()
    }

    IO.println("🤖 Запуск Long Polling...") >> loop
  }

  /**
   * Отправить текстовое сообщение в чат (plain text, без форматирования).
   */
  def sendMessage(text: String): IO[Unit] = sendRawMessage(text, None)

  /**
   * Отправить сообщение с форматированием (по умолчанию MarkdownV2).
   *
   * MarkdownV2 требует экранирования спецсимволов (_, *, [, ], и т.д.)
   * — см. Summary.escapeMarkdown()
   */
  def sendMessageFormatted(text: String, parseMode: String = "MarkdownV2"): IO[Unit] =
    sendRawMessage(text, Some(parseMode))

  /**
   * Ответить на конкретное сообщение (reply).
   * Используется, если нужно ответить на команду пользователя.
   */
  def replyToMessage(messageId: Int, text: String): IO[Unit] = sendRawMessage(text, None, Some(messageId))

  /**
   * Получить информацию о боте (проверка токена).
   *
   * Вызывается при старте приложения, чтобы убедиться,
   * что токен валидный и бот вообще существует.
   * Если токен невалидный — выбросит исключение и приложение упадёт.
   */
  def getMe(): IO[BotInfo] = IO {
    val url = s"${config.apiUrl}/bot${config.botToken}/getMe"
    val request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .GET()
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    val json = response.body()

    val node = objectMapper.readTree(json)
    val result = node.get("result")

    BotInfo(
      id = result.get("id").asLong(),
      username = result.get("username").asText()
    )
  }

  /**
   * Остановить слушатель (graceful shutdown).
   * Устанавливает running = false, и цикл loop завершится на следующей итерации.
   */
  def stop(): IO[Unit] = IO {
    running = false
    println("🛑 Long polling остановлен")
  }

  /**
   * Отправить приветственное сообщение при старте бота.
   * Пользователи в чате увидят, что бот заработал.
   */
  def sendWelcomeMessage(): IO[Unit] = {
    val welcome =
      """
        |🤖 *Hustlers Ljubljana Bot* запущен\!
        |
        |Я буду следить за чатом и делать саммари обсуждений\.
        |
        |*Команды:*
        |/summary — получить сводку последних сообщений
        |/stats — статистика активности
        """.stripMargin
    sendMessageFormatted(welcome)
  }

  /**
   * Внутренний метод для отправки сообщения через HTTP POST.
   *
   * Собирает JSON-тело вручную (без библиотек сериализации) —
   * это простой запрос с 2-3 полями, поэтому Jackson здесь избыточен.
   *
   * @param text             текст сообщения
   * @param parseMode        режим форматирования (None = plain text, Some("MarkdownV2") и т.д.)
   * @param replyToMessageId если задан — ответить на конкретное сообщение
   */
  private def sendRawMessage(text: String, parseMode: Option[String] = None, replyToMessageId: Option[Int] = None): IO[Unit] = IO {
    val url = s"${config.apiUrl}/bot${config.botToken}/sendMessage"

    val json = parseMode match {
      case Some(mode) if replyToMessageId.isDefined =>
        s"""{"chat_id":"${config.targetChatId}","text":"${escapeJson(text)}","parse_mode":"$mode","reply_to_message_id":${replyToMessageId.get}}"""
      case Some(mode) =>
        s"""{"chat_id":"${config.targetChatId}","text":"${escapeJson(text)}","parse_mode":"$mode"}"""
      case None if replyToMessageId.isDefined =>
        s"""{"chat_id":"${config.targetChatId}","text":"${escapeJson(text)}","reply_to_message_id":${replyToMessageId.get}}"""
      case None =>
        s"""{"chat_id":"${config.targetChatId}","text":"${escapeJson(text)}"}"""
    }

    val request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

    if (response.statusCode() != 200) {
      throw new RuntimeException(s"Ошибка отправки сообщения: ${response.statusCode()} - ${response.body()}")
    }
  }

  /**
   * Экранирование спецсимволов для JSON-строк.
   *
   * Необходимо, потому что мы собираем JSON вручную через string interpolation.
   * Без экранирования символы вроде \n или " сломают JSON.
   */
  private def escapeJson(text: String): String = {
    text
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
  }
}

/**
 * Информация о боте (ответ от getMe).
 */
case class BotInfo(id: Long, username: String)