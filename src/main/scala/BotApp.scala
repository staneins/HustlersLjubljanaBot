import cats.effect.{IO, Resource}
import cats.syntax.all._
import clients.TelegramClient
import com.typesafe.scalalogging.LazyLogging
import config.Config
import repository.MessageRepository
import scheduler.SummaryScheduler
import service.SummaryService

/**
 * TODO: Реализовать оркестратор приложения.
 *
 * Подсказка для Java-разработчика:
 * Это как @SpringBootApplication класс, который собирает все бины вместе.
 * В Spring Boot Spring Container собирает зависимости автоматически.
 * В Scala собираем вручную (чистый DI без фреймворка).
 *
 * Resource[IO, A] — это паттерн для управления жизненным циклом.
 * Аналог try-with-resources в Java:
 *   try (Connection conn = dataSource.getConnection()) { ... }
 *
 * В Scala:
 *   Resource.make(acquire)(release).use { resource =>
 *     // работа с ресурсом
 *   }
 */
class BotApp(
  config: Config,
  repo: MessageRepository,
  telegramClient: TelegramClient,
  summaryService: SummaryService,
  scheduler: SummaryScheduler
) extends LazyLogging {

  /**
   * TODO: Запустить приложение.
   *
   * Алгоритм:
   * 1. Отправить приветственное сообщение
   * 2. Запустить telegramClient.startListening() и scheduler.start() параллельно
   *
   * Параллельный запуск в Scala:
   *   (io1, io2).parTupled
   *
   * Это как CompletableFuture.allOf(future1, future2) в Java.
   *
   * for-comprehension — синтаксический сахар для flatMap/map:
   *   for {
   *     _ <- telegramClient.sendWelcomeMessage()
   *     _ <- (telegramClient.startListening(), scheduler.start()).parTupled
   *   } yield ()
   */
  def run(): IO[Unit] = {
    IO.println("TODO: BotApp.run()") >>
    IO.println(s"Chat ID: ${config.targetChatId}")
  }

  /**
   * TODO: Graceful shutdown.
   *
   * Нужно остановить все компоненты в правильном порядке:
   * 1. Остановить планировщик
   * 2. Остановить Telegram клиент
   * 3. Закрыть соединение с MongoDB
   */
  def shutdown(): IO[Unit] = {
    IO.println("TODO: shutdown()")
  }
}

/**
 * Companion object — аналог static/main в Java.
 */
object BotApp extends LazyLogging {

  /**
   * TODO: Собрать и запустить приложение.
   *
   * Алгоритм сборки:
   * 1. Загрузить конфиг (Config.load())
   * 2. Создать репозиторий (MessageRepository.create(config))
   * 3. Создать TelegramClient
   * 4. Создать SummaryService
   * 5. Создать SummaryScheduler
   * 6. Создать BotApp
   * 7. Запустить app.run()
   *
   * Resource — для автоматического закрытия ресурсов:
   *   val resource = for {
   *     config <- Resource.eval(Config.load())
   *     repo   <- Resource.make(MessageRepository.create(config))(_.close())
   *     ...
   *   } yield new BotApp(...)
   *
   *   resource.use(app => app.run())
   *
   * Это как @Bean(destroyMethod = "close") в Spring Boot.
   */
  def run: IO[Unit] = {
    IO.println("TODO: Собрать приложение и запустить")
  }
}