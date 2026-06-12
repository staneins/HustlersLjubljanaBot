package scheduler

import cats.effect.{IO, Ref}
import cats.syntax.all._
import clients.TelegramClient
import config.Config
import repository.MessageRepository
import service.SummaryService

import scala.concurrent.duration._

/**
 * Планировщик периодической генерации саммари.
 *
 * Работает как бесконечный цикл: ждёт N минут → генерирует саммари →
 * отправляет в Telegram → чистит БД → повторяет.
 *
 * Ошибки в одном цикле логируются, но не останавливают планировщик.
 */
class SummaryScheduler(
  summaryService: SummaryService,          // Сервис генерации саммари (Groq + fallback)
  telegramClient: TelegramClient,          // Клиент Telegram для отправки сообщений
  repo: MessageRepository[IO],             // Репозиторий MongoDB для очистки после саммари
  config: Config                           // Конфигурация (интервал, lookback)
) {

  // Флаг "работает ли планировщик". Ref — это потокобезопасная ячейка в IO.
  // Можно менять из разных файберов без гонок.
  private val running: Ref[IO, Boolean] = Ref.unsafe[IO, Boolean](true)

  /**
   * Запустить бесконечный цикл планировщика.
   *
   * Каждая итерация:
   * 1. Проверить, не остановлен ли планировщик
   * 2. Выполнить runOnce() с обработкой ошибок
   * 3. Поспать interval минут
   * 4. Повторить
   */
  def start(): IO[Unit] = {
    val interval = config.summaryIntervalMinutes.minutes

    def loop(): IO[Unit] = for {
      isRunning <- running.get
      _ <- if (isRunning) {
        runOnce().handleErrorWith { err =>
          // Ошибка в одном цикле логируется, но НЕ останавливает планировщик
          IO.println(s"❌ Ошибка в планировщике: ${err.getMessage}")
        } >>
          IO.sleep(interval) >> // Ждём до следующего цикла
          loop()
      } else {
        IO.unit
      }
    } yield ()

    IO.println(s"⏰ Планировщик запущен. Интервал: ${config.summaryIntervalMinutes} мин") >>
      loop()
  }

  /**
   * Один цикл: генерация саммари + отправка в чат + очистка БД.
   *
   * Поток:
   * 1. Проверить, есть ли сообщения за период
   * 2. Сгенерировать саммари через SummaryService (Groq API)
   * 3. Отправить в Telegram
   * 4. Удалить ВСЕ сообщения из MongoDB (БД чиста до следующего цикла)
   */
  def runOnce(): IO[Unit] = {
    val now = System.currentTimeMillis() / 1000                     // Текущее время в секундах
    val periodStart = now - config.lookbackMinutes * 60             // Начало периода (сейчас - lookback)

    for {
      // Шаг 1: Проверить, есть ли сообщения за период
      hasMessages <- summaryService.hasMessagesInPeriod(periodStart, now)
      _ <- if (hasMessages) {
        for {
          // Шаг 2: Сгенерировать саммари (AI через Groq или fallback на статистику)
          summary <- summaryService.generateSummary()
          _ <- if (summary.totalMessages > 0) {
            for {
              // Шаг 3: Отправить саммари в Telegram-чат
              _ <- telegramClient.sendMessageFormatted(summary.text)
              _ <- IO.println(s"✅ Саммари отправлено: ${summary.totalMessages} сообщений")

              // Шаг 4: Очистить MongoDB — все сообщения уже обработаны
              deleted <- repo.deleteAll()
              _ <- IO.println(s"🗑️ MongoDB очищена: удалено $deleted сообщений")
            } yield ()
          } else {
            IO.unit
          }
        } yield ()
      } else {
        IO.println("ℹ️ За период нет сообщений, саммари не отправляется")
      }
    } yield ()
  }

  /**
   * Остановить планировщик (graceful shutdown).
   * Устанавливает флаг running = false, и цикл завершится на следующей итерации.
   */
  def stop(): IO[Unit] = {
    running.set(false) >>
      IO.println("⏹️ Планировщик остановлен")
  }
}

object SummaryScheduler {
  def create(
    summaryService: SummaryService,
    telegramClient: TelegramClient,
    repo: MessageRepository[IO],
    config: Config
  ): SummaryScheduler =
    new SummaryScheduler(summaryService, telegramClient, repo, config)
}
