package scheduler

import cats.effect.{IO, Ref}
import cats.syntax.all._
import clients.TelegramClient
import config.Config
import repository.MessageRepository
import service.SummaryService

import scala.concurrent.duration._

/**
 * Scheduler for periodic summary generation.
 *
 * Works as an infinite loop: wait N minutes → generate summary →
 * send to Telegram → clean DB → repeat.
 *
 * Errors in a single cycle are logged but do not stop the scheduler.
 */
class SummaryScheduler(
  summaryService: SummaryService,          // Summary generation service (Groq + fallback)
  telegramClient: TelegramClient,          // Telegram client for sending messages
  repo: MessageRepository[IO],             // MongoDB repository for cleanup after summary
  config: Config                           // Configuration (interval, lookback)
) {

  // Flag "is scheduler running". Ref is a thread-safe cell in IO.
  // Can be modified from different fibers without race conditions.
  private val running: Ref[IO, Boolean] = Ref.unsafe[IO, Boolean](true)

  /**
   * Start the infinite scheduler loop.
   *
   * Each iteration:
   * 1. Check if scheduler is stopped
   * 2. Execute runOnce() with error handling
   * 3. Sleep for interval minutes
   * 4. Repeat
   */
  def start(): IO[Unit] = {
    val interval = config.summaryIntervalMinutes.minutes

    def loop(): IO[Unit] = for {
      isRunning <- running.get
      _ <- if (isRunning) {
        runOnce().handleErrorWith { err =>
          // Error in a single cycle is logged, but does NOT stop the scheduler
          IO.println(s"❌ Scheduler error: ${err.getMessage}")
        } >>
          IO.sleep(interval) >> // Wait until next cycle
          loop()
      } else {
        IO.unit
      }
    } yield ()

    IO.println(s"⏰ Scheduler started. Interval: ${config.summaryIntervalMinutes} min") >>
      loop()
  }

  /**
   * Single cycle: summary generation + send to chat + DB cleanup.
   *
   * Flow:
   * 1. Check if there are messages for the period
   * 2. Generate summary via SummaryService (Groq API)
   * 3. Send to Telegram
   * 4. Delete ALL messages from MongoDB (DB is clean until next cycle)
   */
  def runOnce(): IO[Unit] = {
    val now = System.currentTimeMillis() / 1000                     // Current time in seconds
    val periodStart = now - config.lookbackMinutes * 60             // Start of period (now - lookback)

    for {
      // Step 1: Check if there are messages for the period
      hasMessages <- summaryService.hasMessagesInPeriod(periodStart, now)
      _ <- if (hasMessages) {
        for {
          // Step 2: Generate summary (AI via Groq or fallback to statistics)
          summary <- summaryService.generateSummary()
          _ <- if (summary.totalMessages > 0) {
            for {
              // Step 3: Send summary to Telegram chat
              _ <- telegramClient.sendMessageFormatted(summary.text)
              _ <- IO.println(s"✅ Summary sent: ${summary.totalMessages} messages")

              // Step 4: Clean MongoDB — all messages are already processed
              deleted <- repo.deleteAll()
              _ <- IO.println(s"🗑️ MongoDB cleaned: deleted $deleted messages")
            } yield ()
          } else {
            IO.unit
          }
        } yield ()
      } else {
        IO.println("ℹ️ No messages for the period, summary not sent")
      }
    } yield ()
  }

  /**
   * Stop the scheduler (graceful shutdown).
   * Sets flag running = false, and the loop will complete on the next iteration.
   */
  def stop(): IO[Unit] = {
    running.set(false) >>
      IO.println("⏹️ Scheduler stopped")
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
