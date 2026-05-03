package config

/**
 * TODO: Реализовать загрузку конфигурации.
 *
 * Подсказка для Java-разработчика:
 * В Spring Boot ты бы использовал @ConfigurationProperties:
 *   @ConfigurationProperties(prefix = "bot")
 *   public record BotConfig(String token, long targetChatId) {}
 *
 * В Scala с pureconfig это работает похоже:
 * - Создаёшь case class (аналог record)
 * - pureconfig автоматически мапит application.conf на case class
 *
 * Структура application.conf:
 *   bot {
 *     token = "123:ABC"
 *     target-chat-id = 123456
 *   }
 *   summary {
 *     interval-minutes = 60
 *     lookback-minutes = 60
 *   }
 *   mongodb {
 *     uri = "mongodb://localhost:27017"
 *     database = "bot"
 *   }
 *
 * Поля в case class должны называться так же, как в конфиге
 * (kebab-case в конфиге -> camelCase в Scala).
 */

case class BotConfig(
  token: String,
  targetChatId: Long
)

case class SummaryConfig(
  intervalMinutes: Int,
  lookbackMinutes: Int
)

case class MongoConfig(
  uri: String,
  database: String
)

case class Config(
  bot: BotConfig,
  summary: SummaryConfig,
  mongodb: MongoConfig
)

object Config {
  /**
   * TODO: Реализовать загрузку через pureconfig + переменные окружения.
   *
   * Подсказка для Java-разработчика:
   * В Spring Boot @Value("${ENV:default}") или System.getenv().
   *
   * В Scala:
   *   sys.env.get("TELEGRAM_BOT_TOKEN") // Option[String], как Optional
   *
   * Чтобы загрузить pureconfig:
   *   import pureconfig._
   *   import pureconfig.generic.auto._
   *   ConfigSource.default.load[Config]
   *
   * Вернёт Either[ConfigReaderFailures, Config] — это как Result/ Either в других языках.
   * Left = ошибка, Right = успех.
   */
  def load(): Either[String, Config] = {
    // TODO:
    // 1. Загрузить из pureconfig
    // 2. Переопределить переменными окружения (sys.env)
    // 3. Валидировать (token не пустой, chatId > 0)
    Left("TODO: Реализовать загрузку конфига")
  }

  /**
   * Удобные accessor'ы — как getter'ы в Java record.
   * В Scala можно добавлять методы через implicit class (расширения).
   */
  implicit class ConfigOps(c: Config) {
    def botToken: String = c.bot.token
    def targetChatId: Long = c.bot.targetChatId
    def summaryIntervalMinutes: Int = c.summary.intervalMinutes
    def lookbackMinutes: Int = c.summary.lookbackMinutes
    def mongodbUri: String = c.mongodb.uri
    def mongodbDatabase: String = c.mongodb.database
  }
}
