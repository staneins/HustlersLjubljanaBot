import cats.effect.{IO, IOApp}
import config.Config
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.auto._

/**
 * Точка входа в приложение.
 *
 * Подсказка для Java-разработчика:
 * Это как SpringApplication.run(BotApplication.class, args) в Spring Boot.
 *
 * В Scala с Cats Effect используется IOApp вместо main(String[] args).
 * IOApp управляет жизненным циклом приложения:
 * - Создаёт ExecutionContext (аналог пула потоков)
 * - Обрабатывает graceful shutdown (SIGINT/SIGTERM)
 * - Запускает IO эффект
 *
 * IOApp.Simple — упрощённая версия, которая возвращает IO[Unit]
 * (аналог void метода в Java).
 */
object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    ConfigSource.default.load[Config] match {
      case Right(config) =>
        IO.println(s"Загружен конфиг: ${config.botToken.take(10)}...")
      case Left(error) =>
        IO.raiseError(new Exception(s"Ошибка конфига: $error"))
    }
  }
}