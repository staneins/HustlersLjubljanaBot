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
 * Summary generation service for chat discussions.
 *
 * Retrieves messages for a period from MongoDB, formats them,
 * and sends to Groq API for AI summary generation.
 */
class SummaryService(
  val repo: MessageRepository[IO],
  groqClient: GroqClient,
  config: Config
) {

  // Formatter for displaying dates in summary (human-readable format)
  private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())

  /**
   * Generate summary for the last N minutes (from config).
   *
   * Automatically calculates period: [now - lookbackMinutes, now].
   * Main method called by the scheduler.
   */
  def generateSummary(): IO[Summary] = {
    val now = System.currentTimeMillis() / 1000
    val periodStart = now - config.lookbackMinutes * 60
    generateSummaryForPeriod(periodStart, now)
  }

  /**
   * Generate summary for a specific period.
   *
   * Logic:
   * 1. Get messages from MongoDB for the period
   * 2. Filter empty messages
   * 3. If messages exist — send to Groq AI
   * 4. If empty — return empty summary
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
   * Generate AI summary via Groq API.
   *
   * Flow:
   * 1. Format messages into readable text
   * 2. Send to Groq LLM
   * 3. Build final text: statistics + top participants + AI text
   * 4. If Groq unavailable — fallback to simple statistics
   */
  private def generateAiSummary(messages: List[Message], start: Long, end: Long): IO[Summary] = {
    // Format all messages into a single text for AI
    val messagesText = formatMessages(messages)

    // Send to Groq and collect result
    groqClient.generateSummary(messagesText).map { aiText =>
      // Collect statistics by authors: who wrote how much
      val byAuthor = messages.groupBy(_.author).view.mapValues(_.size).toMap
      val sortedAuthors = byAuthor.toList.sortBy(-_._2) // Sort descending
      val startStr = formatter.format(Instant.ofEpochSecond(start))
      val endStr = formatter.format(Instant.ofEpochSecond(end))

      // Заголовок саммари с общей статистикой
      val header = s"📊 *Chat Summary*\n\nPeriod: $startStr — $endStr\nTotal messages: ${messages.size}\nParticipants: ${byAuthor.size}\n\n"

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
        "*Top Participants:*\n" + top3.mkString("\n") + "\n\n"
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
      // Fallback: if Groq API is unavailable (network error, rate limits, etc.) —
      // return a simple summary based on statistics (without AI).
      // The application continues to work even without AI access.
      IO.println(s"⚠️ Groq API error: ${err.getMessage}. Using fallback summary.") >>
        IO.pure(Summary.fromMessages(messages, start, end))
    }
  }

  /**
   * Format messages for AI submission.
   */
  private def formatMessages(messages: List[Message]): String = {
    messages.map { msg =>
      val time = formatter.format(Instant.ofEpochSecond(msg.date))
      s"[$time] ${msg.author}: ${msg.text}"
    }.mkString("\n")
  }

  /**
   * Check if there are messages for the period.
   */
  def hasMessagesInPeriod(start: Long, end: Long): IO[Boolean] = {
    repo.countInPeriod(start, end).map(_ > 0)
  }
}

object SummaryService {
  def create(repo: MessageRepository[IO], groqClient: GroqClient, config: Config): SummaryService =
    new SummaryService(repo, groqClient, config)
}
