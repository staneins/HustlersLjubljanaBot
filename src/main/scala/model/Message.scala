package model

/**
 * Доменная модель сообщения для хранения в MongoDB.
 *
 * Это основная сущность приложения — каждое сообщение из чата
 * сохраняется в таком виде. Содержит только нужные поля:
 * - messageId для дедупликации (уникальный индекс в MongoDB)
 * - author — имя отправителя (username или firstName)
 * - text — текст сообщения
 * - date — Unix-таймстемп (секунды), используется для фильтрации по периоду
 * - chatId — ID чата (на случай если бот будет в нескольких чатах)
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
   * Конвертация из Telegram DTO в доменную модель.
   *
   * Возвращает Option[Message], потому что:
   * - Сообщение может быть без текста (фото, стикер и т.д.)
   * - Мы сохраняем только текстовые сообщения
   *
   * Автор определяется так:
   * 1. Если есть username — берём его
   * 2. Иначе — firstName
   * 3. Если вообще ничего нет — "Unknown"
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
        date = msg.timestamp,   // Unix-таймстемп в секундах (от Telegram)
        chatId = msg.chat.id
      )
    }
  }
}
