package repository

import model.Message

/**
 * Message repository trait.
 *
 * Defines CRUD operations contract for messages in MongoDB.
 * Parameterized by effect type F[_] (usually IO) to allow
 * swapping implementation for testing.
 */
trait MessageRepository[F[_]] {

  /** Save a single message to DB */
  def create(message: Message): F[Message]

  /** Get all messages from DB */
  def findAll: F[List[Message]]

  /** Get messages for a period (by Unix timestamp in seconds) */
  def findBetween(start: Long, end: Long): F[List[Message]]

  /** Count messages for a period */
  def countInPeriod(start: Long, end: Long): F[Long]

  /** Delete ALL messages from collection (called after successful summary send) */
  def deleteAll(): F[Long]

  /** Close MongoDB connection */
  def close(): F[Unit]
}
