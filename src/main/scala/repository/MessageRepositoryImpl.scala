package repository

import cats.effect.IO
import com.mongodb.client.{MongoClient, MongoClients, MongoCollection, MongoDatabase}
import com.mongodb.client.model.{Filters, IndexOptions, Indexes}
import org.bson.Document

import model.Message
import config.Config

/**
 * MongoDB implementation of the message repository via MongoDB Java Driver.
 *
 * Uses synchronous driver wrapped in `IO.blocking` to avoid
 * blocking the main Cats Effect thread pool.
 *
 * @param client     — MongoClient (needed for close())
 * @param db         — MongoDB database
 * @param collection — "messages" collection
 */
class MessageRepositoryImpl(
  client: MongoClient,
  db: MongoDatabase,
  collection: MongoCollection[Document]
) extends MessageRepository[IO] {

  /**
   * Save a message to MongoDB.
   *
   * Creates a BSON document from the Message domain model fields
   * and inserts it into the collection. If messageId is duplicated —
   * DuplicateKeyException will be thrown (unique index).
   */
  override def create(message: Message): IO[Message] = IO.blocking {
    val doc = new Document()
      .append("messageId", message.messageId) // Telegram message ID (for deduplication)
      .append("author", message.author)       // Author name (username or firstName)
      .append("text", message.text)           // Message text
      .append("date", message.date)           // Unix timestamp (seconds)
      .append("chatId", message.chatId)       // Chat ID

    collection.insertOne(doc)
    message
  }

  /**
   * Get all messages from the collection.
   * Used mainly for debugging.
   */
  override def findAll: IO[List[Message]] = IO.blocking {
    import scala.jdk.CollectionConverters._
    collection.find()
      .into(new java.util.ArrayList[Document]())
      .asScala.toList
      .flatMap(fromDocument) // flatMap because fromDocument returns Option
  }

  /**
   * Find messages for a period (between start and end inclusive).
   *
   * Uses MongoDB filter: { date: { $gte: start, $lte: end } }.
   * Index on "date" field speeds up this query.
   *
   * @param start period start (Unix timestamp, seconds)
   * @param end   period end (Unix timestamp, seconds)
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
   * Count messages for a period.
   * More efficient query than findBetween when only numbers are needed.
   */
  override def countInPeriod(start: Long, end: Long): IO[Long] = IO.blocking {
    val filter = new Document()
      .append("date", new Document().append("$gte", start).append("$lte", end))

    collection.countDocuments(filter)
  }

  /**
   * Delete ALL documents from the collection.
   *
   * Called after successfully sending a summary to the chat,
   * to avoid storing already processed messages.
   *
   * @return number of deleted documents
   */
  override def deleteAll(): IO[Long] = IO.blocking {
    // Filters.empty() — filter that matches all documents (like "WHERE 1=1" in SQL)
    val result = collection.deleteMany(Filters.empty())
    result.getDeletedCount
  }

  /**
   * Close MongoDB connection.
   * Called during graceful shutdown via Resource.
   */
  override def close(): IO[Unit] = IO.blocking {
    client.close()
  }

  /**
   * Convert MongoDB BSON document to domain Message model.
   *
   * Returns Option because some fields may be missing in the document
   * (e.g., if document was saved by an older bot version).
   */
  private def fromDocument(doc: Document): Option[Message] = {
    for {
      // classOf[java.lang.Integer] is needed due to type erasure on JVM —
      // Scala cannot simply write classOf[Int] for boxed types
      messageId <- Option(doc.get("messageId", classOf[java.lang.Integer])).map(_.intValue())
      author    <- Option(doc.getString("author"))
      text      <- Option(doc.getString("text"))
      date      <- Option(doc.get("date", classOf[java.lang.Long])).map(_.longValue())
      chatId    <- Option(doc.get("chatId", classOf[java.lang.Long])).map(_.longValue())
    } yield Message(messageId, author, text, date, chatId)
  }
}

/**
 * Companion object — factory method for creating the repository.
 *
 * Creates MongoClient, gets database and collection,
 * then creates necessary indexes.
 */
object MessageRepositoryImpl {

  /**
   * Create a repository instance with MongoDB connection.
   *
   * Creates two indexes:
   * 1. On "date" field — for fast period search (findBetween)
   * 2. Unique on "messageId" — to prevent duplicates during Long Polling
   */
  def create(config: Config): IO[MessageRepositoryImpl] = IO.blocking {
    val client = MongoClients.create(config.mongodbUri)
    val db = client.getDatabase(config.mongodbDatabase)
    val collection = db.getCollection("messages")

    // Index on date for fast period search
    collection.createIndex(Indexes.ascending("date"))

    // Unique index on messageId to prevent duplicates
    try {
      collection.createIndex(Indexes.ascending("messageId"), new IndexOptions().unique(true))
    } catch {
      case _: Exception => // Index already exists — this is normal on re-run
    }

    new MessageRepositoryImpl(client, db, collection)
  }
}