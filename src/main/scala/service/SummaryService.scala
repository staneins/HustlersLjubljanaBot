package service

import cats.effect.IO
import config.Config
import dto.{Message, Summary}
import repository.MessageRepository

/**
 * TODO: Реализовать сервис генерации саммари.
 *
 * Подсказка для Java-разработчика:
 * Это как @Service в Spring Boot.
 * Зависимости через конструктор — val repo (public final в Java).
 *
 * В Scala val в конструкторе = public final поле:
 *   class SummaryService(val repo: MessageRepository, config: Config)
   *
 * Эквивалент в Java:
 *   public class SummaryService {
 *     public final MessageRepository repo;
 *     private final Config config;
 *     public SummaryService(MessageRepository repo, Config config) {
 *       this.repo = repo;
 *       this.config = config;
 *     }
 *   }
 */
class SummaryService(val repo: MessageRepository, config: Config) {

  /**
   * TODO: Сгенерировать саммари за последние N минут.
   *
   * Алгоритм:
   * 1. Получить текущее время в секундах: System.currentTimeMillis() / 1000
   * 2. Вычислить periodStart = now - config.lookbackMinutes * 60
   * 3. Вызвать generateSummaryForPeriod(start, end)
   *
   * В Scala System.currentTimeMillis() — тот же метод, что и в Java.
   */
  def generateSummary(): IO[Summary] = {
    // TODO: Вычислить период и вызвать generateSummaryForPeriod
    IO.println("TODO: generateSummary()") >> IO.pure(
      Summary.emptySummary(0, 0)
    )
  }

  /**
   * TODO: Сгенерировать саммари за конкретный период.
   *
   * Алгоритм:
   * 1. Получить сообщения из репозитория: repo.getBetween(start, end)
   * 2. Отфильтровать пустые
   * 3. Если нет сообщений — вернуть emptySummary
   * 4. Иначе — вызвать generateSimpleSummary
   *
   * for-comprehension в Scala:
   *   for {
   *     messages <- repo.getBetween(start, end)
   *     valid = messages.filterNot(_.isEmpty)
   *     summary <- if (valid.isEmpty) IO.pure(Summary.emptySummary(start, end))
   *                else IO.pure(generateSimpleSummary(valid, start, end))
   *   } yield summary
   *
   * Это как flatMap в Stream/Mono:
   *   repo.getBetween(start, end)
   *     .flatMap(messages -> { ... })
   */
  def generateSummaryForPeriod(start: Long, end: Long): IO[Summary] = {
    // TODO: Реализовать
    IO.println(s"TODO: generateSummaryForPeriod($start, $end)") >> IO.pure(
      Summary.emptySummary(start, end)
    )
  }

  /**
   * TODO: Сгенерировать простое саммари (статистика без AI).
   *
   * Делегирует в Summary.fromMessages — фабричный метод в companion object.
   */
  def generateSimpleSummary(messages: List[Message], start: Long, end: Long): Summary = {
    // TODO: Вызвать Summary.fromMessages(messages, start, end)
    Summary.emptySummary(start, end)
  }

  /**
   * TODO (бонус): Саммари с использованием AI API.
   *
   * Варианты:
   * 1. Ollama (локально): POST http://localhost:11434/api/generate
   * 2. Google Gemini: POST https://generativelanguage.googleapis.com/...  
   * 3. Groq: POST https://api.groq.com/openai/v1/chat/completions
   *
   * В Java ты бы использовал RestTemplate или WebClient.
   * В Scala можно использовать тот же HttpClient, что и в TelegramClient.
   */
  def generateAISummary(messages: List[Message]): IO[Summary] = {
    // TODO: Подключить AI API
    IO.println("TODO: generateAISummary()") >> IO.pure(
      Summary.emptySummary(0, 0)
    )
  }
}

object SummaryService {
  /**
   * Factory method — аналог @Bean factory в Spring.
   */
  def create(repo: MessageRepository, config: Config): SummaryService =
    new SummaryService(repo, config)
}