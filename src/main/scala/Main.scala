import cats.effect.IOApp

/**
 * Application entry point.
 *
 * IOApp.Simple is a base Cats Effect trait for applications.
 * It manages the lifecycle:
 * - Creates execution ExecutionContext (thread pool)
 * - Handles SIGINT/SIGTERM signals for graceful shutdown
 * - Runs the run() method and waits for completion
 *
 * All actual work is delegated to BotApp.runApp,
 * which assembles components and starts the bot.
 */
object Main extends IOApp.Simple {
  def run: cats.effect.IO[Unit] = BotApp.runApp
}
