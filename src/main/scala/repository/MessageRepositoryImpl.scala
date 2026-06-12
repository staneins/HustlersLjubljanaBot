package repository

import cats.effect.IO
import com.mongodb.client.{MongoClient, MongoClients, MongoCollection, MongoDatabase}
import com.mongodb.client.model.{Filters, IndexOptions, Indexes}
import org.bson.Document

import model.Message
import config.Config

/**
 * Реализация репозитория сообщений через MongoDB Java Driver.
 *
 * Используется синхронный драйвер, обёрнутый в `IO.blocking`, чтобы
 * не блокировать основной поток Cats Effect.
 *
 * @param client     — MongoClient (нужен для close())
 * @param db         — база данных MongoDB
 * @param collection — коллекция "messages"
 */
class MessageRepositoryImpl(
  client: MongoClient,
  db: MongoDatabase,
  collection: MongoCollection[Document]
) extends MessageRepository[IO] {

  /**
   * Сохранить сообщение в MongoDB.
   *
   * Создаёт BSON-документ из полей доменной модели Message
   * и вставляет его в коллекцию. Если messageId дублируется —
   * выбросится DuplicateKeyException (уникальный индекс).
   */
  override def create(message: Message): IO[Message] = IO.blocking {
    val doc = new Document()
      .append("messageId", message.messageId) // ID сообщения в Telegram (для дедупликации)
      .append("author", message.author)       // Имя автора (username или firstName)
      .append("text", message.text)           // Текст сообщения
      .append("date", message.date)           // Unix-таймстемп (секунды)
      .append("chatId", message.chatId)       // ID чата

    collection.insertOne(doc)
    message
  }

  /**
   * Получить все сообщения из коллекции.
   * Используется в основном для отладки.
   */
  override def findAll: IO[List[Message]] = IO.blocking {
    import scala.jdk.CollectionConverters._
    collection.find()
      .into(new java.util.ArrayList[Document]())
      .asScala.toList
      .flatMap(fromDocument) // flatMap потому что fromDocument возвращает Option
  }

  /**
   * Найти сообщения за период (между start и end включительно).
   *
   * Использует фильтр MongoDB: { date: { $gte: start, $lte: end } }.
   * Индекс по полю "date" ускоряет этот запрос.
   *
   * @param start начало периода (Unix-таймстемп, секунды)
   * @param end   конец периода (Unix-таймстемп, секунды)
   */
  override def findBetween(start: Long, end: Long): IO[List[Message]] = IO.blocking {
    import scala.jdk.CollectionConverters._
    val filter = new Document()
      .append("date", new Document().append("$gte", start).append("$lte", end))

    collection.find(filter)
      .into(new java.util.ArrayList[Document]())
      .asScala.toList
      .flatMap(fromDocument)
  }

  /**
   * Подсчитать количество сообщений за период.
   * Более эффективный запрос чем findBetween, когда нужны только цифры.
   */
  override def countInPeriod(start: Long, end: Long): IO[Long] = IO.blocking {
    val filter = new Document()
      .append("date", new Document().append("$gte", start).append("$lte", end))

    collection.countDocuments(filter)
  }

  /**
   * Удалить ВСЕ документы из коллекции.
   *
   * Вызывается после успешной отправки саммари в чат,
   * чтобы не хранить уже обработанные сообщения.
   *
   * @return количество удалённых документов
   */
  override def deleteAll(): IO[Long] = IO.blocking {
    // Filters.empty() — фильтр, который матчит все документы (аналог "WHERE 1=1" в SQL)
    val result = collection.deleteMany(Filters.empty())
    result.getDeletedCount
  }

  /**
   * Закрыть соединение с MongoDB.
   * Вызывается при graceful shutdown через Resource.
   */
  override def close(): IO[Unit] = IO.blocking {
    client.close()
  }

  /**
   * Преобразование BSON-документа MongoDB в доменную модель Message.
   *
   * Возвращает Option, потому что какое-то поле может отсутствовать в документе
   * (например, если документ был сохранён старой версией бота).
   */
  private def fromDocument(doc: Document): Option[Message] = {
    for {
      // classOf[java.lang.Integer] нужен из-за type erasure в JVM —
      // Scala не может просто написать classOf[Int] для boxed-типов
      messageId <- Option(doc.get("messageId", classOf[java.lang.Integer])).map(_.intValue())
      author    <- Option(doc.getString("author"))
      text      <- Option(doc.getString("text"))
      date      <- Option(doc.get("date", classOf[java.lang.Long])).map(_.longValue())
      chatId    <- Option(doc.get("chatId", classOf[java.lang.Long])).map(_.longValue())
    } yield Message(messageId, author, text, date, chatId)
  }
}

/**
 * Companion object — фабричный метод для создания репозитория.
 *
 * Создаёт MongoClient, получает базу данных и коллекцию,
 * затем создаёт необходимые индексы.
 */
object MessageRepositoryImpl {

  /**
   * Создать экземпляр репозитория с подключением к MongoDB.
   *
   * Создаёт два индекса:
   * 1. По полю "date" — для быстрого поиска за период (findBetween)
   * 2. Уникальный по "messageId" — для предотвращения дублей при Long Polling
   */
  def create(config: Config): IO[MessageRepositoryImpl] = IO.blocking {
    val client = MongoClients.create(config.mongodbUri)
    val db = client.getDatabase(config.mongodbDatabase)
    val collection = db.getCollection("messages")

    // Индекс по date для быстрого поиска за период
    collection.createIndex(Indexes.ascending("date"))

    // Уникальный индекс по messageId для предотвращения дублей
    try {
      collection.createIndex(Indexes.ascending("messageId"), new IndexOptions().unique(true))
    } catch {
      case _: Exception => // Индекс уже существует — это нормально при повторном запуске
    }

    new MessageRepositoryImpl(client, db, collection)
  }
}