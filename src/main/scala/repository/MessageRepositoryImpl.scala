package repository

import cats.effect.Async
import cats.syntax.functor._
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.Filter
import mongo4cats.bson.ObjectId

import model.Message

class MessageRepositoryImpl[F[_]: Async](
                                       collection: MongoCollection[F, Message]
                                     ) extends MessageRepository[F] {

  override def create(user: Message): F[Message] =
    collection.insertOne(user).as(user)   // после вставки возвращаем исходный объект

  override def findById(id: ObjectId): F[Option[Message]] =
    collection.find(Filter.eq("_id", id)).first

  override def findAll: F[List[Message]] =
     collection.find.all.map(_.toList)
}
