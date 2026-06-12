import cats.effect.{IO, Resource}
import cats.syntax.all._
import clients.{GroqClient, TelegramClient}
import com.typesafe.scalalogging.LazyLogging
import config.Config
import repository.MessageRepositoryImpl
import scheduler.SummaryScheduler
import service.SummaryService

/**
 * Application orchestrator.
 *
 * Assembles all components in the correct order and manages their lifecycle.
 * Works as a DI container (like Spring ApplicationContext, but manual).
 *
 * Runs two parallel processes:
 * 1. TelegramClient.startListening() — Long Polling for saving messages
 * 2. SummaryScheduler.start() — periodic summary generation and sending
 */
class BotApp(
  config: Config,
  repo: MessageRepositoryImpl,
  telegramClient: TelegramClient,
  summaryService: SummaryService,
  scheduler: SummaryScheduler
) extends LazyLogging {

  /**
   * Run the application.
   *
   * Flow:
   * 1. Verify bot token via getMe()
   * 2. Send welcome message
   * 3. Launch listener and scheduler in parallel via parTupled
   */
  def run(): IO[Unit] = {
    // Initialization: token check, welcome message
    val setup = for {
      _ <- IO.println("🚀 Starting Hustlers Ljubljana Bot...")
      botInfo <- telegramClient.getMe()
      _ <- IO.println(s"✅ Bot authorized: @${botInfo.username}")
      _ <- telegramClient.sendWelcomeMessage()
      _ <- IO.println("⚙️ Starting parallel processes...")
    } yield ()

    // Two parallel processes: listener and scheduler.
    // parTupled — runs both IOs in separate fibers simultaneously.
    // If one fails — handleErrorWith converts error to IO.never (hangs forever)
    val processes = (
      telegramClient.startListening().handleErrorWith(err =>
        IO.println(s"❌ Listener error: ${err.getMessage}") >> IO.never
      ),
      scheduler.start().handleErrorWith(err =>
        IO.println(s"❌ Scheduler error: ${err.getMessage}") >> IO.never
      )
    ).parTupled

    setup >> processes >> IO.never
  }

  /**
   * Graceful shutdown.
   *
   * Stops components in reverse order:
   * 1. Scheduler (stops generating new summaries)
   * 2. Listener (stops receiving messages from Telegram)
   * 3. MongoDB (closes connection)
   */
  def shutdown(): IO[Unit] = {
    logger.info("Starting graceful shutdown...")
    for {
      _ <- IO.println("Stopping scheduler...")
      _ <- scheduler.stop().handleErrorWith(err => IO.println(s"Scheduler stop error: $err"))

      _ <- IO.println("Stopping listener...")
      _ <- telegramClient.stop().handleErrorWith(err => IO.println(s"Client stop error: $err"))

      _ <- IO.println("Closing MongoDB connection...")
      _ <- repo.close().handleErrorWith(err => IO.println(s"Repository close error: $err"))

      _ <- IO.println("Bot stopped.")
    } yield ()
  }
}

/**
 * Companion object — factory method for assembling and running the application.
 *
 * Uses cats.effect.Resource for lifecycle management:
 * - Resource.make(acquire)(release) — creates resource and guarantees cleanup
 * - Resource.use — runs the application and calls release on completion
 *
 * This is analogous to try-with-resources in Java, but for IO effects.
 */
object BotApp extends LazyLogging {

  /**
   * Assemble and run the application.
   *
   * Component creation order matters — each depends on previous ones:
   * 1. Config (no dependencies)
   * 2. MongoDB repo (needs Config)
   * 3. TelegramClient (needs Config + repo)
   * 4. GroqClient (needs Config)
   * 5. SummaryService (needs repo + GroqClient + Config)
   * 6. SummaryScheduler (needs SummaryService + TelegramClient + repo + Config)
   * 7. BotApp (needs all of the above)
   */
  def runApp: IO[Unit] = {
    // Resource guarantees that repo.close() is called on completion (success or failure)
    // to close MongoDB connection
    val appResource: Resource[IO, BotApp] = for {
      // 1. Load config
      config <- Resource.eval(loadConfig)
      _ <- Resource.eval(IO.println(s"Config loaded. Chat ID: ${config.targetChatId}"))

      // 2. Create MongoDB repository
      // Resource.make — creates repo and guarantees close() call on cleanup
      repo <- Resource.make(
        MessageRepositoryImpl.create(config)
      )(_.close())

      // 3. Create TelegramClient
      telegramClient = new TelegramClient(config, repo)

      // 4. Verify token
      _ <- Resource.eval(
        telegramClient.getMe().flatMap(info =>
          IO.println(s"Connected to bot @${info.username}")
        ).handleErrorWith(err =>
          IO.println(s"Telegram connection error: $err") >> IO.raiseError(err)
        )
      )

      // 5. Create GroqClient for AI summaries
      groqClient = GroqClient.create(config)

      // 6. Create SummaryService
      summaryService = SummaryService.create(repo, groqClient, config)

      // 7. Create SummaryScheduler (pass repo for DB cleanup after summary)
      scheduler = SummaryScheduler.create(summaryService, telegramClient, repo, config)

      // 8. Create BotApp
    } yield new BotApp(config, repo, telegramClient, summaryService, scheduler)

    // use — runs the application. On completion, cleanup from Resource is automatically called
    appResource.use { app =>
      app.run()
    }.handleErrorWith { err =>
      logger.error("Critical application error", err)
      IO.println(s"Application crashed: ${err.getMessage}")
    }
  }

  /**
   * Load config or fail with a clear error.
   */
  private def loadConfig: IO[Config] = {
    Config.load() match {
      case Right(config) => IO.pure(config)
      case Left(error) => IO.raiseError(new RuntimeException(error))
    }
  }
}