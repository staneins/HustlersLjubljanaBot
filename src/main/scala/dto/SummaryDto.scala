package dto

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

case class Summary(
  periodStart: Long,
  periodEnd: Long,
  totalMessages: Int,
  uniqueAuthors: Int,
  messagesByAuthor: Map[String, Int],
  topAuthors: List[(String, Int)],
  text: String
)

object Summary {

  private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())

  def fromMessages(messages: List[model.Message], start: Long, end: Long): Summary = {
    val validMessages = messages.filter(_.text.trim.nonEmpty)

    val byAuthor = validMessages.groupBy(_.author).view.mapValues(_.size).toMap
    val sortedAuthors = byAuthor.toList.sortBy(-_._2)
    val top3 = sortedAuthors.take(3)

    val startStr = formatter.format(Instant.ofEpochSecond(start))
    val endStr = formatter.format(Instant.ofEpochSecond(end))

    val text = buildSummaryText(startStr, endStr, validMessages.size, byAuthor, top3)

    Summary(
      periodStart = start,
      periodEnd = end,
      totalMessages = validMessages.size,
      uniqueAuthors = byAuthor.size,
      messagesByAuthor = byAuthor,
      topAuthors = top3,
      text = text
    )
  }

  def emptySummary(start: Long, end: Long): Summary = {
    val startStr = formatter.format(Instant.ofEpochSecond(start))
    val endStr = formatter.format(Instant.ofEpochSecond(end))

    Summary(
      periodStart = start,
      periodEnd = end,
      totalMessages = 0,
      uniqueAuthors = 0,
      messagesByAuthor = Map.empty,
      topAuthors = List.empty,
      text = s"📊 Chat Summary\n\nPeriod: $startStr — $endStr\n\nNo messages during this period."
    )
  }

  private def buildSummaryText(
    startStr: String,
    endStr: String,
    total: Int,
    byAuthor: Map[String, Int],
    top3: List[(String, Int)]
  ): String = {
    val header = s"📊 Chat Summary\n\nPeriod: $startStr — $endStr\nTotal messages: $total\nParticipants: ${byAuthor.size}"

    val topSection = if (top3.nonEmpty) {
      val lines = top3.zipWithIndex.map { case ((author, count), idx) =>
        val medal = idx match {
          case 0 => "🥇"
          case 1 => "🥈"
          case 2 => "🥉"
          case _ => "•"
        }
        s"$medal $author: $count"
      }
      s"\n\nTop Participants:\n${lines.mkString("\n")}"
    } else ""

    val detailsSection = if (byAuthor.nonEmpty) {
      val lines = byAuthor.toList.sortBy(-_._2).map { case (author, count) =>
        s"• $author: $count"
      }
      s"\n\nAll Participants:\n${lines.mkString("\n")}"
    } else ""

    header + topSection + detailsSection
  }

  def escapeMarkdown(text: String): String = {
    val charsToEscape = Set('_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!', '\\')
    text.flatMap { c =>
      if (charsToEscape.contains(c)) "\\" + c else c.toString
    }
  }
}