package clients

import cats.effect.IO
import config.Config
import model.Message
import repository.MessageRepository

/**
 * TODO: Реализовать клиент Telegram Bot API.
 *
 * Подсказка для Java-разработчика:
 * Это как @Service в Spring Boot, но без @Autowired.
 * Зависимости передаём через конструктор (constructor injection) —
 * точно так же, как в хорошем Spring коде.
 *
 * В Java:
 *   @Service
 *   public class TelegramClient {
 *     private final Config config;
 *     private final MessageRepository repo;
 *     public TelegramClient(Config config, MessageRepository repo) { ... }
 *   }
 *
 * В Scala класс с конструктором выглядит так же:
 *   class TelegramClient(config: Config, repo: MessageRepository) { ... }
 *
 * IO[Unit] — это аналог CompletableFuture<Void> или Mono<Void> в Reactor.
 * Вся работа с сетью оборачивается в IO, чтобы быть "чистой" (функциональный стиль).
 */
class TelegramClient(config: Config, repo: MessageRepository) {

  /**
   * TODO: Запустить Long Polling для получения обновлений.
   *
   * Подсказка для Java-разработчика:
   * В Spring WebFlux ты бы использовал WebClient:
   *   webClient.get().uri("/getUpdates?offset=" + offset).retrieve()...
   *
   * Здесь используем java.net.http.HttpClient (встроенный в JDK 11+).
   * В Scala можно вызывать Java API напрямую.
   *
   * Long Polling:
   * 1. Отправить GET /getUpdates?offset=X&timeout=30
   * 2. Распарсить JSON (Jackson)
   * 3. Обработать каждое update
   * 4. Обновить offset (чтобы не получать дубли)
   * 5. Повторить
   *
   * Бесконечный цикл в Scala:
   *   def loop(): IO[Unit] = for {
   *     _ <- doSomething()
   *     _ <- IO.sleep(1.second)
   *     _ <- loop() // рекурсия!
   *   } yield ()
   *
   * Это как while(true), но без стек-оверфлоу (tail recursion оптимизация).
   */
  def startListening(): IO[Unit] = {
    // TODO: Реализовать long polling
    IO.println("TODO: startListening()")
  }

  /**
   * TODO: Отправить текстовое сообщение в чат.
   *
   * POST /sendMessage
   * Body: {"chat_id": "123", "text": "Привет"}
   *
   * В Java с HttpClient:
   *   HttpRequest request = HttpRequest.newBuilder()
   *     .uri(URI.create(url))
   *     .header("Content-Type", "application/json")
   *     .POST(HttpRequest.BodyPublishers.ofString(json))
   *     .build();
   *   HttpResponse<String> response = httpClient.send(request, ...);
   *
   * В Scala то же самое, но синтаксис чуть компактнее.
   */
  def sendMessage(text: String): IO[Unit] = {
    // TODO: POST /sendMessage
    IO.println(s"TODO: sendMessage($text)")
  }

  /**
   * TODO: Отправить сообщение с Markdown-форматированием.
   *
   * parse_mode: "MarkdownV2" или "HTML"
   */
  def sendMessageFormatted(text: String, parseMode: String = "MarkdownV2"): IO[Unit] = {
    // TODO: POST /sendMessage с parse_mode
    IO.println(s"TODO: sendMessageFormatted($text, $parseMode)")
  }

  /**
   * TODO: Ответить на конкретное сообщение.
   *
   * reply_to_message_id в теле запроса.
   */
  def replyToMessage(messageId: Int, text: String): IO[Unit] = {
    // TODO: POST /sendMessage с reply_to_message_id
    IO.println(s"TODO: replyToMessage($messageId, $text)")
  }

  /**
   * TODO: Получить информацию о боте (проверка токена).
   *
   * GET /getMe
   */
  def getMe(): IO[Unit] = {
    // TODO: GET /getMe, распарсить JSON, вывести имя бота
    IO.println("TODO: getMe()")
  }

  /**
   * TODO: Остановить слушатель.
   *
   * Можно использовать @volatile Boolean flag (как в Java).
   * Или Ref[IO, Boolean] (функциональный аналог AtomicBoolean).
   */
  def stop(): IO[Unit] = {
    IO.println("TODO: stop()")
  }

  /**
   * TODO: Отправить приветственное сообщение при старте.
   */
  def sendWelcomeMessage(): IO[Unit] = {
    // TODO: Красивое сообщение с Markdown
    IO.println("TODO: sendWelcomeMessage()")
  }
}

/**
 * Companion object — аналог static методов/полей в Java.
 * В Scala принято выносить вспомогательные вещи сюда.
 */
object TelegramClient {
  /**
   * TODO: Можно добавить case class для ответа getMe.
   *
   * В Java: record BotInfo(Long id, String username) {}
   * В Scala: case class BotInfo(id: Long, username: String)
   */
}
