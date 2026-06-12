package dto

import com.fasterxml.jackson.annotation.{JsonProperty, JsonIgnoreProperties}

/**
 * Response from Telegram API for /getUpdates method.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
case class GetUpdatesResponse(
  @JsonProperty("ok") ok: Boolean,
  @JsonProperty("result") updates: List[Update]
)

/**
 * Single update from Telegram.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
case class Update(
  @JsonProperty("update_id") updateId: Int,
  @JsonProperty("message") message: Option[TelegramMessage]
)

/**
 * Message in Telegram API format (nested object inside Update).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
case class TelegramMessage(
  @JsonProperty("message_id") messageId: Int,
  @JsonProperty("from") from: Option[From],
  @JsonProperty("chat") chat: Chat,
  @JsonProperty("date") timestamp: Long,
  @JsonProperty("text") text: Option[String],
  @JsonProperty("edit_date") editDate: Option[Int] = None
)

/**
 * Information about the sender.
 * @param isPremium — Telegram Premium flag (may be absent for old accounts)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
case class From(
  @JsonProperty("id") id: Long,
  @JsonProperty("first_name") firstName: String,
  @JsonProperty("username") username: Option[String] = None,
  @JsonProperty("is_bot") isBot: Boolean = false,
  @JsonProperty("is_premium") isPremium: Option[Boolean] = None
)

/**
 * Information about the chat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
case class Chat(
  @JsonProperty("id") id: Long,
  @JsonProperty("type") `type`: String,
  @JsonProperty("first_name") firstName: Option[String] = None,
  @JsonProperty("username") username: Option[String] = None
)
