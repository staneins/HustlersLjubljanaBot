package dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Ответ от Telegram API для метода /getUpdates.
 */
case class GetUpdatesResponse(
  @JsonProperty("ok") ok: Boolean,
  @JsonProperty("result") updates: List[Update]
)

/**
 * Отдельное обновление от Telegram.
 */
case class Update(
  @JsonProperty("update_id") updateId: Int,
  @JsonProperty("message") message: Option[TelegramMessage]
)

/**
 * Сообщение в формате Telegram API (вложенный объект внутри Update).
 */
case class TelegramMessage(
  @JsonProperty("message_id") messageId: Int,
  @JsonProperty("from") from: Option[From],
  @JsonProperty("chat") chat: Chat,
  @JsonProperty("date") timestamp: Long,
  @JsonProperty("text") text: Option[String],
  @JsonProperty("edit_date") editDate: Option[Int] = None
)

/**
 * Информация об отправителе.
 */
case class From(
  @JsonProperty("id") id: Long,
  @JsonProperty("first_name") firstName: String,
  @JsonProperty("username") username: Option[String] = None,
  @JsonProperty("is_bot") isBot: Boolean = false
)

/**
 * Информация о чате.
 */
case class Chat(
  @JsonProperty("id") id: Long,
  @JsonProperty("type") `type`: String,
  @JsonProperty("first_name") firstName: Option[String] = None,
  @JsonProperty("username") username: Option[String] = None
)
