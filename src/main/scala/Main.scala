import cats.effect.IOApp

/**
 * Точка входа в приложение.
 *
 * IOApp.Simple — это базовый трейт Cats Effect для приложений.
 * Он управляет жизненным циклом:
 * - Создаёт вычислительный ExecutionContext (пул потоков)
 * - Обрабатывает сигналы SIGINT/SIGTERM для graceful shutdown
 * - Запускает метод run() и ждёт его завершения
 *
 * Вся реальная работа делегируется в BotApp.runApp,
 * который собирает компоненты и запускает бота.
 */
object Main extends IOApp.Simple {
  def run: cats.effect.IO[Unit] = BotApp.runApp
}
