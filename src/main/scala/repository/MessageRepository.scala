package repository

import model.Message
import mongo4cats.bson.ObjectId

trait MessageRepository[F[_]] {
  def create(message: Message): F[Message]
  def findById(id: ObjectId): F[Option[Message]]
  def findAll: F[List[Message]]
}
