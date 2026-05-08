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

/**
 * Модель сообщения из Telegram чата.
 *
 * В Scala case class автоматически даёт:
 * - конструктор (как @Data в Lombok)
 * - equals/hashCode
 * - toString
 * - copy()
 * - apply() / unapply() в companion object
 *
 * Это аналог record в Java 17 или @Data класса в Lombok.
 */
case class Message(
  @JsonProperty("message_id")messageId: Int,
  from: From,
  chat: Chat,
  text: String,
  date: Long,
)

object Message {
  /**
   * TODO: Реализовать конвертацию из JSON ответа Telegram API.
   *
   * Подсказка для Java-разработчика:
   * В Java ты бы использовал ObjectMapper.readValue(json, Message.class).
   * В Scala то же самое, но типы Option вместо nullable.
   *
   * Как в Spring Boot:
   *   JsonNode node = objectMapper.readTree(json);
   *   String text = node.get("text").asText();
   *
   * В Scala:
   *   val node: JsonNode = mapper.readTree(json)
   *   val text: String = node.get("text").asText()
   *
   * Возвращаем Option[Message], потому что сообщение может быть без текста.
   * Это как Optional<Message> в Java — чтобы избежать null.
   */
  def fromTelegramMessage(msg: String): Option[Message] = {
    // TODO: Парсинг JSON через Jackson (ObjectMapper)
    // Пример:
    // val mapper = new ObjectMapper()
    // val node = mapper.readTree(msg)
    // if (node.has("text")) Some(Message(...)) else None
//    val mapper = new ObjectMapper()
//    val update = mapper.readValue(msg, GetUpdatesResponse.getClass)
    ???
  }

  /**
   * TODO: Реализовать конвертацию из MongoDB Document.
   *
   * Подсказка для Java-разработчика:
   * В Spring Data MongoDB ты бы использовал MongoTemplate или Repository.
   * Здесь мы работаем с низкоуровневым драйвером (как MongoTemplate в Spring).
   *
   * Document в MongoDB Java Driver работает как Map<String, Object>.
   * Получение значения:
   *   Java: doc.getLong("id")
   *   Scala: doc.get("id", classOf[java.lang.Long]).longValue()
   *
   * Обрати внимание на classOf[java.lang.Long] — это из-за type erasure в JVM.
   * В Scala нет явного Class<Long>, поэтому используем classOf.
   */
  def fromBsonDocument(doc: org.bson.Document): Message = {
    // TODO: Достать поля из Document и создать Message
    // Пример:
    // Message(
    //   id = doc.get("id", classOf[java.lang.Long]).longValue(),
    //   author = doc.get("author", classOf[String]),
    //   ...
    // )
    ???
  }
}
