package model

import mongo4cats.bson.ObjectId

case class Message(
                    _id: ObjectId = ObjectId(),
                    text: String,
                    date: Long,
                  )
