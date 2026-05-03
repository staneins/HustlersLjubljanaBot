package model

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
  id: Long,
  author: String,
  text: String,
  timestamp: Long,
  messageId: Int = 0,
  isEdited: Boolean = false
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
    None
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
