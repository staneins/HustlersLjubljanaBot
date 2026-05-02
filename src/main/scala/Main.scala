import cats.effect.{IOApp, IO}
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.auto._

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
