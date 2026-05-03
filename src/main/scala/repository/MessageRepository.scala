package repository

import cats.effect.IO
import config.Config
import model.Message
import org.bson.Document

/**
 * TODO: Реализовать хранилище сообщений в MongoDB.
 *
 * Подсказка для Java-разработчика:
 * Это как @Repository в Spring Data MongoDB, но ручная реализация.
 * В Spring Boot ты бы использовал:
 *   interface MessageRepository extends MongoRepository<Message, String> { ... }
 *
 * Здесь мы работаем с низкоуровневым MongoDB Java Driver (как MongoTemplate):
 *   MongoClient client = MongoClients.create(uri);
 *   MongoDatabase db = client.getDatabase("bot");
 *   MongoCollection<Document> collection = db.getCollection("messages");
 *
 * В Scala вызовы Java API работают 1-в-1, но коллекции конвертируются
 * через scala.jdk.CollectionConverters (вместо старого JavaConverters).
 */
class MessageRepository(config: Config) {

  /**
   * TODO: Инициализировать подключение к MongoDB и создать индексы.
   *
   * Подсказка:
   *   val client = MongoClients.create(config.mongodbUri)
   *   val db = client.getDatabase(config.mongodbDatabase)
   *   val collection = db.getCollection("messages", classOf[Document])
   *
   * Индексы (важно для производительности!):
   *   collection.createIndex(Indexes.ascending("timestamp"))
   *   collection.createIndex(Indexes.ascending("messageId"), new IndexOptions().unique(true))
   *
   * IO оборачивает "эффект" (работу с внешним миром).
   * Это как CompletableFuture.supplyAsync(() -> { ... })
   */
  def initialize(): IO[Unit] = {
    IO.println("TODO: initialize() — подключение к MongoDB и индексы")
  }

  /**
   * TODO: Сохранить одно сообщение.
   *
   * Возвращает true если сохранено, false если дубль (messageId уже есть).
   *
   * В Java:
   *   try {
   *     collection.insertOne(document);
   *     return true;
   *   } catch (MongoWriteException e) {
   *     if (e.getError().getCode() == 11000) return false;
   *     throw e;
   *   }
   *
   * В Scala catch пишется иначе:
   *   try { ... } catch {
   *     case e: MongoWriteException => ...
   *   }
   */
  def save(message: Message): IO[Boolean] = {
    IO.println(s"TODO: save($message)") >> IO.pure(false)
  }

  /**
   * TODO: Получить сообщения с указанного timestamp.
   *
   * В Java с MongoDB Driver:
   *   collection.find(Filters.gte("timestamp", timestamp))
   *     .sort(new Document("timestamp", 1))
   *     .iterator()
   *
   * Конвертация итератора в List:
   *   Java: List<Message> list = new ArrayList<>();
   *         while (iterator.hasNext()) list.add(...);
   *
   *   Scala: iterator.asScala.toList.map(doc => ...)
   *
   * Нужен импорт: import scala.jdk.CollectionConverters._
   */
  def getSince(timestamp: Long): IO[List[Message]] = {
    IO.println(s"TODO: getSince($timestamp)") >> IO.pure(List.empty)
  }

  /**
   * TODO: Получить сообщения в диапазоне [start, end].
   *
   * Используй Filters.and(Filters.gte(...), Filters.lte(...))
   */
  def getBetween(start: Long, end: Long): IO[List[Message]] = {
    IO.println(s"TODO: getBetween($start, $end)") >> IO.pure(List.empty)
  }

  /**
   * TODO: Удалить сообщения старше timestamp.
   *
   * Возвращает количество удалённых.
   */
  def deleteOlderThan(timestamp: Long): IO[Int] = {
    IO.println(s"TODO: deleteOlderThan($timestamp)") >> IO.pure(0)
  }

  /**
   * TODO: Общее количество сообщений.
   */
  def countAll(): IO[Long] = {
    IO.println("TODO: countAll()") >> IO.pure(0L)
  }

  /**
   * TODO: Получить список уникальных авторов.
   *
   * Используй collection.distinct("author", classOf[String])
   */
  def getUniqueAuthors(): IO[List[String]] = {
    IO.println("TODO: getUniqueAuthors()") >> IO.pure(List.empty)
  }

  /**
   * TODO: Проверить существование сообщения по messageId.
   */
  def exists(messageId: Int): IO[Boolean] = {
    IO.println(s"TODO: exists($messageId)") >> IO.pure(false)
  }

  /**
   * TODO: Очистить все сообщения (для тестов).
   */
  def clear(): IO[Unit] = {
    IO.println("TODO: clear()")
  }

  /**
   * TODO: Проверить наличие сообщений за период.
   */
  def hasMessagesInPeriod(start: Long, end: Long): IO[Boolean] = {
    IO.println(s"TODO: hasMessagesInPeriod($start, $end)") >> IO.pure(false)
  }

  /**
   * TODO: Закрыть соединение с MongoDB.
   *
   * Не забудь вызвать client.close()
   */
  def close(): IO[Unit] = {
    IO.println("TODO: close()")
  }
}

object MessageRepository {
  /**
   * Factory method — как @Bean в Spring Config.
   *
   * Создаёт репозиторий и сразу инициализирует.
   * Возвращает IO[MessageRepository] — "ленивое" создание.
   */
  def create(config: Config): IO[MessageRepository] = {
    val repo = new MessageRepository(config)
    repo.initialize().as(repo)
    // TODO: Убедись, что .as() доступен — нужен импорт cats.syntax.functor._
    // или используй: repo.initialize().map(_ => repo)
  }
}
