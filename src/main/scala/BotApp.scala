import cats.effect.{IO, Resource}
import cats.syntax.all._
import clients.{GroqClient, TelegramClient}
import com.typesafe.scalalogging.LazyLogging
import config.Config
import repository.MessageRepositoryImpl
import scheduler.SummaryScheduler
import service.SummaryService

/**
 * Оркестратор приложения.
 *
 * Собирает все компоненты в правильном порядке и управляет их жизненным циклом.
 * Работает как DI-контейнер (аналог Spring ApplicationContext, но вручную).
 *
 * Запускает два параллельных процесса:
 * 1. TelegramClient.startListening() — Long Polling для сохранения сообщений
 * 2. SummaryScheduler.start() — периодическая генерация и отправка саммари
 */
class BotApp(
  config: Config,
  repo: MessageRepositoryImpl,
  telegramClient: TelegramClient,
  summaryService: SummaryService,
  scheduler: SummaryScheduler
) extends LazyLogging {

  /**
   * Запустить приложение.
   *
   * Поток:
   * 1. Проверить токен бота через getMe()
   * 2. Отправить приветственное сообщение
   * 3. Запустить listener и scheduler параллельно через parTupled
   */
  def run(): IO[Unit] = {
    // Инициализация: проверка токена, приветствие
    val setup = for {
      _ <- IO.println("🚀 Запуск Hustlers Ljubljana Bot...")
      botInfo <- telegramClient.getMe()
      _ <- IO.println(s"✅ Бот авторизован: @${botInfo.username}")
      _ <- telegramClient.sendWelcomeMessage()
      _ <- IO.println("⚙️ Запуск параллельных процессов...")
    } yield ()

    // Два параллельных процесса: listener и scheduler.
    // parTupled — запускает оба IO в отдельных файберах одновременно.
    // Если один упадёт — handleErrorWith превратит ошибку в IO.never (висит вечно)
    val processes = (
      telegramClient.startListening().handleErrorWith(err =>
        IO.println(s"❌ Ошибка в listener: ${err.getMessage}") >> IO.never
      ),
      scheduler.start().handleErrorWith(err =>
        IO.println(s"❌ Ошибка в scheduler: ${err.getMessage}") >> IO.never
      )
    ).parTupled

    setup >> processes >> IO.never
  }

  /**
   * Graceful shutdown.
   *
   * Останавливает компоненты в обратном порядке:
   * 1. Планировщик (перестаёт генерировать новые саммари)
   * 2. Listener (перестаёт получать сообщения из Telegram)
   * 3. MongoDB (закрывает соединение)
   */
  def shutdown(): IO[Unit] = {
    logger.info("Запуск graceful shutdown...")
    for {
      _ <- IO.println("Остановка планировщика...")
      _ <- scheduler.stop().handleErrorWith(err => IO.println(s"Ошибка остановки scheduler: $err"))

      _ <- IO.println("Остановка listener...")
      _ <- telegramClient.stop().handleErrorWith(err => IO.println(s"Ошибка остановки client: $err"))

      _ <- IO.println("Закрытие соединения с MongoDB...")
      _ <- repo.close().handleErrorWith(err => IO.println(s"Ошибка закрытия репозитория: $err"))

      _ <- IO.println("Бот остановлен.")
    } yield ()
  }
}

/**
 * Companion object — фабричный метод для сборки и запуска приложения.
 *
 * Использует cats.effect.Resource для управления жизненным циклом:
 * - Resource.make(acquire)(release) — создаёт ресурс и гарантирует cleanup
 * - Resource.use — запускает приложение и при завершении вызывает release
 *
 * Это аналог try-with-resources в Java, но для IO-эффектов.
 */
object BotApp extends LazyLogging {

  /**
   * Собрать и запустить приложение.
   *
   * Порядок создания компонентов важен — каждый следующий зависит от предыдущих:
   * 1. Config (ни от чего не зависит)
   * 2. MongoDB repo (нужен Config)
   * 3. TelegramClient (нужен Config + repo)
   * 4. GroqClient (нужен Config)
   * 5. SummaryService (нужен repo + GroqClient + Config)
   * 6. SummaryScheduler (нужен SummaryService + TelegramClient + repo + Config)
   * 7. BotApp (нужно всё вышеперечисленное)
   */
  def runApp: IO[Unit] = {
    // Resource гарантирует, что при завершении (успешном или аварийном)
    // вызовется repo.close() для закрытия соединения с MongoDB
    val appResource: Resource[IO, BotApp] = for {
      // 1. Загрузить конфиг
      config <- Resource.eval(loadConfig)
      _ <- Resource.eval(IO.println(s"Конфиг загружен. Chat ID: ${config.targetChatId}"))

      // 2. Создать репозиторий MongoDB
      // Resource.make — создаёт repo и гарантирует вызов close() при cleanup
      repo <- Resource.make(
        MessageRepositoryImpl.create(config)
      )(_.close())

      // 3. Создать TelegramClient
      telegramClient = new TelegramClient(config, repo)

      // 4. Проверить токен
      _ <- Resource.eval(
        telegramClient.getMe().flatMap(info =>
          IO.println(s"Подключено к боту @${info.username}")
        ).handleErrorWith(err =>
          IO.println(s"Ошибка подключения к Telegram: $err") >> IO.raiseError(err)
        )
      )

      // 5. Создать GroqClient для AI-саммари
      groqClient = GroqClient.create(config)

      // 6. Создать SummaryService
      summaryService = SummaryService.create(repo, groqClient, config)

      // 7. Создать SummaryScheduler (передаём repo для очистки БД после саммари)
      scheduler = SummaryScheduler.create(summaryService, telegramClient, repo, config)

      // 8. Создать BotApp
    } yield new BotApp(config, repo, telegramClient, summaryService, scheduler)

    // use — запускает приложение. При завершении автоматически вызовется cleanup из Resource
    appResource.use { app =>
      app.run()
    }.handleErrorWith { err =>
      logger.error("Критическая ошибка приложения", err)
      IO.println(s"Приложение аварийно остановлено: ${err.getMessage}")
    }
  }

  /**
   * Загрузить конфиг или упасть с понятной ошибкой.
   */
  private def loadConfig: IO[Config] = {
    Config.load() match {
      case Right(config) => IO.pure(config)
      case Left(error) => IO.raiseError(new RuntimeException(error))
    }
  }
}