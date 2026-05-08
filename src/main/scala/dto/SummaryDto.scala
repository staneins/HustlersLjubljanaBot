package dto

/**
 * Модель сгенерированного саммари.
 *
 * В Scala case class автоматически даёт:
 * - конструктор (как @Data в Lombok)
 * - equals/hashCode
 * - toString
 * - copy()
 * - apply() / unapply() в companion object
 */
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
  /**
   * TODO: Создать Summary из списка сообщений.
   *
   * Подсказка для Java-разработчика:
   * В Java ты бы использовал Stream API:
   *   Map<String, Long> byAuthor = messages.stream()
   *     .collect(Collectors.groupingBy(Message::getAuthor, Collectors.counting()));
   *
   * В Scala группировка короче:
   *   val byAuthor = messages.groupBy(_.author).mapValues(_.size)
   *
   * Сортировка в Scala:
   *   Java: list.sort(Comparator.comparingInt(...).reversed())
   *   Scala: list.sortBy(-_._2)
   *
   * List[(String, Int)] — это список пар (tuple).
   * В Java аналог — List<Map.Entry<String, Integer>> или record Pair(String, Integer).
   */
  def fromMessages(messages: List[Message], start: Long, end: Long): Summary = {
    // TODO:
    // 1. Отфильтровать пустые сообщения
    // 2. Сгруппировать по автору и посчитать
    // 3. Найти топ-3 автора
    // 4. Сгенерировать текст с Markdown
    // 5. Вернуть Summary(...)
    ???
  }

  /**
   * TODO: Создать пустое саммари, когда за период нет сообщений.
   *
   * Это как factory method в Java.
   */
  def emptySummary(start: Long, end: Long): Summary = {
    // TODO: Вернуть Summary с totalMessages = 0 и текстом "Нет сообщений"
    ???
  }
}