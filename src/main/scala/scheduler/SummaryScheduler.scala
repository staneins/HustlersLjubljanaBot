package scheduler

import cats.effect.IO
import clients.TelegramClient
import config.Config
import service.SummaryService

/**
 * TODO: Реализовать планировщик периодической генерации саммари.
 *
 * Подсказка для Java-разработчика:
 * Это как @Scheduled в Spring Boot:
 *   @Scheduled(fixedRate = 60, timeUnit = TimeUnit.MINUTES)
 *   public void generateSummary() { ... }
 *
 * Но вместо аннотаций пишем цикл вручную.
 * В функциональном стиле используем рекурсию вместо while(true).
 */
class SummaryScheduler(
  summaryService: SummaryService,
  telegramClient: TelegramClient,
  config: Config
) {

  /**
   * TODO: Запустить бесконечный цикл планировщика.
   *
   * Алгоритм:
   * 1. Вызвать runOnce()
   * 2. Подождать config.summaryIntervalMinutes минут
   * 3. Повторить (если running == true)
   *
   * Ожидание в Scala:
   *   IO.sleep(60.minutes)
   *
   * Нужен импорт: scala.concurrent.duration._
   *
   * Бесконечный цикл через рекурсию:
   *   def loop(): IO[Unit] = for {
   *     _ <- runOnce()
   *     _ <- IO.sleep(interval)
   *     _ <- if (running) loop() else IO.unit
   *   } yield ()
   *
   * handleErrorWith — обработка ошибок без остановки:
   *   someIO.handleErrorWith { e =>
   *     logger.error("Ошибка", e)
   *     IO.unit // продолжаем
   *   }
   */
  def start(): IO[Unit] = {
    IO.println("TODO: start() — запуск планировщика") >>
    IO.println(s"Интервал: ${config.summaryIntervalMinutes} мин")
  }

  /**
   * TODO: Один цикл: генерация + отправка саммари.
   *
   * Алгоритм:
   * 1. Получить текущее время
   * 2. Вычислить periodStart = now - lookbackMinutes * 60
   * 3. Проверить наличие сообщений: repo.hasMessagesInPeriod(start, end)
   * 4. Если есть — сгенерировать summaryService.generateSummaryForPeriod(start, end)
   * 5. Если summary не пустое — отправить telegramClient.sendMessageFormatted(summary.text)
   * 6. Логировать результат
   */
  def runOnce(): IO[Unit] = {
    // TODO: Реализовать один цикл
    IO.println("TODO: runOnce()")
  }

  /**
   * TODO: Остановить планировщик.
   *
   * Можно использовать @volatile Boolean (как в Java).
   * Или более функциональный подход — Ref[IO, Boolean].
   *
   * @volatile var running: Boolean = true
   *
   * В Java: private volatile boolean running = true;
   */
  def stop(): IO[Unit] = {
    IO.println("TODO: stop()")
  }
}

object SummaryScheduler {
  /**
   * Factory method.
   */
  def create(
    summaryService: SummaryService,
    telegramClient: TelegramClient,
    config: Config
  ): SummaryScheduler =
    new SummaryScheduler(summaryService, telegramClient, config)
}