package repository

import model.Message

/**
 * Треит репозитория сообщений.
 *
 * Определяет контракт CRUD-операций над сообщениями в MongoDB.
 * Параметризован эффект-типом F[_] (обычно IO), чтобы можно было
 * подменять реализацию для тестирования.
 */
trait MessageRepository[F[_]] {

  /** Сохранить одно сообщение в БД */
  def create(message: Message): F[Message]

  /** Получить все сообщения из БД */
  def findAll: F[List[Message]]

  /** Получить сообщения за период (по Unix-таймстемпу в секундах) */
  def findBetween(start: Long, end: Long): F[List[Message]]

  /** Подсчитать количество сообщений за период */
  def countInPeriod(start: Long, end: Long): F[Long]

  /** Удалить ВСЕ сообщения из коллекции (вызывается после успешной отправки саммари) */
  def deleteAll(): F[Long]

  /** Закрыть соединение с MongoDB */
  def close(): F[Unit]
}
