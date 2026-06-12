package service

import cats.effect.IO
import cats.syntax.all._
import clients.GroqClient
import config.Config
import dto.Summary
import model.Message
import repository.MessageRepository

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Сервис генерации саммари обсуждений.
 *
 * Получает сообщения за период из MongoDB, форматирует их и отправляет
 * в Groq API для генерации AI-саммари.
 */
class SummaryService(
  val repo: MessageRepository[IO],
  groqClient: GroqClient,
  config: Config
) {

  // Форматтер для отображения дат в саммари (человеко-читаемый вид)
  private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())

  /**
   * Сгенерировать саммари за последние N минут (из конфига).
   *
   * Вычисляет период автоматически: [сейчас - lookbackMinutes, сейчас].
   * Основной метод, вызываемый планировщиком.
   */
  def generateSummary(): IO[Summary] = {
    val now = System.currentTimeMillis() / 1000
    val periodStart = now - config.lookbackMinutes * 60
    generateSummaryForPeriod(periodStart, now)
  }

  /**
   * Сгенерировать саммари за конкретный период.
   *
   * Логика:
   * 1. Получить сообщения из MongoDB за период
   * 2. Отфильтровать пустые сообщения
   * 3. Если есть сообщения — отправить в Groq AI
   * 4. Если пусто — вернуть пустое саммари
   */
  def generateSummaryForPeriod(start: Long, end: Long): IO[Summary] = {
    for {
      messages <- repo.findBetween(start, end)
      valid = messages.filter(_.text.trim.nonEmpty)
      summary <- if (valid.isEmpty) {
        IO.pure(Summary.emptySummary(start, end))
      } else {
        generateAiSummary(valid, start, end)
      }
    } yield summary
  }

  /**
   * Сгенерировать AI-саммари через Groq API.
   *
   * Поток:
   * 1. Форматировать сообщения в читаемый текст
   * 2. Отправить в Groq LLM
   * 3. Собрать итоговый текст: статистика + топ участников + AI-текст
   * 4. Если Groq недоступен — fallback на простую статистику
   */
  private def generateAiSummary(messages: List[Message], start: Long, end: Long): IO[Summary] = {
    // Форматируем все сообщения в один текст для отправки в AI
    val messagesText = formatMessages(messages)

    // Отправляем в Groq и собираем результат
    groqClient.generateSummary(messagesText).map { aiText =>
      // Собираем статистику по авторам: кто сколько написал
      val byAuthor = messages.groupBy(_.author).view.mapValues(_.size).toMap
      val sortedAuthors = byAuthor.toList.sortBy(-_._2) // Сортируем по убыванию
      val startStr = formatter.format(Instant.ofEpochSecond(start))
      val endStr = formatter.format(Instant.ofEpochSecond(end))

      // Заголовок саммари с общей статистикой
      val header = s"📊 *Саммари чата*\n\nПериод: $startStr — $endStr\nВсего сообщений: ${messages.size}\nУчастников: ${byAuthor.size}\n\n"

      // Топ-3 самых активных участника с медалями
      val topSection = if (sortedAuthors.nonEmpty) {
        val top3 = sortedAuthors.take(3).zipWithIndex.map { case ((author, count), idx) =>
          val medal = idx match {
            case 0 => "🥇"
            case 1 => "🥈"
            case 2 => "🥉"
            case _ => "•"
          }
          s"$medal ${Summary.escapeMarkdown(author)}: $count"
        }
        "*Топ участников:*\n" + top3.mkString("\n") + "\n\n"
      } else ""

      // Итоговый текст: заголовок + топ + AI-саммари
      val fullText = header + topSection + aiText

      Summary(
        periodStart = start,
        periodEnd = end,
        totalMessages = messages.size,
        uniqueAuthors = byAuthor.size,
        messagesByAuthor = byAuthor,
        topAuthors = sortedAuthors.take(3),
        text = fullText
      )
    }.handleErrorWith { err =>
      // Fallback: если Groq API недоступен (ошибка сети, лимиты и т.д.) —
      // возвращаем простое саммари на основе статистики (без AI).
      // Приложение продолжает работать даже без доступа к AI.
      IO.println(s"⚠️ Groq API ошибка: ${err.getMessage}. Используется fallback саммари.") >>
        IO.pure(Summary.fromMessages(messages, start, end))
    }
  }

  /**
   * Форматировать сообщения для отправки в AI.
   */
  private def formatMessages(messages: List[Message]): String = {
    messages.map { msg =>
      val time = formatter.format(Instant.ofEpochSecond(msg.date))
      s"[$time] ${msg.author}: ${msg.text}"
    }.mkString("\n")
  }

  /**
   * Проверить, есть ли сообщения за период.
   */
  def hasMessagesInPeriod(start: Long, end: Long): IO[Boolean] = {
    repo.countInPeriod(start, end).map(_ > 0)
  }
}

object SummaryService {
  def create(repo: MessageRepository[IO], groqClient: GroqClient, config: Config): SummaryService =
    new SummaryService(repo, groqClient, config)
}
