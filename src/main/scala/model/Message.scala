package model

/**
 * Domain model for messages stored in MongoDB.
 *
 * This is the main application entity — each chat message
 * is stored in this format. Contains only necessary fields:
 * - messageId for deduplication (unique index in MongoDB)
 * - author — sender name (username or firstName)
 * - text — message text
 * - date — Unix timestamp (seconds), used for period filtering
 * - chatId — chat ID (in case bot runs in multiple chats)
 */
case class Message(
  messageId: Int,
  author: String,
  text: String,
  date: Long,
  chatId: Long
)

object Message {
  /**
   * Convert from Telegram DTO to domain model.
   *
   * Returns Option[Message] because:
   * - Message may have no text (photo, sticker, etc.)
   * - We only save text messages
   *
   * Author is determined as:
   * 1. If username exists — use it
   * 2. Otherwise — firstName
   * 3. If nothing at all — "Unknown"
   */
  def fromTelegramMessage(msg: dto.TelegramMessage): Option[Message] = {
    msg.text.map { text =>
      val author = msg.from.map { from =>
        from.username.filter(_.nonEmpty).getOrElse(from.firstName)
      }.getOrElse("Unknown")

      Message(
        messageId = msg.messageId,
        author = author,
        text = text,
        date = msg.timestamp,   // Unix timestamp in seconds (from Telegram)
        chatId = msg.chat.id
      )
    }
  }
}
