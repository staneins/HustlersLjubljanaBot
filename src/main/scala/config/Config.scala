package config

import pureconfig.{ConfigSource, ConfigReader}
import pureconfig.generic.auto._
import pureconfig.error.ConfigReaderFailures

/**
 * Telegram bot configuration.
 *
 * @param token        bot token from @BotFather (required via env TELEGRAM_BOT_TOKEN)
 * @param targetChatId chat ID to monitor (required via env TARGET_CHAT_ID)
 */
case class BotConfig(
  token: String,
  targetChatId: Long
)

/**
 * Summary generation configuration.
 *
 * @param intervalMinutes  interval between summary generation (default 1440 = 24 hours)
 * @param lookbackMinutes  how many minutes back to collect messages (default 1440 = 24 hours)
 */
case class SummaryConfig(
  intervalMinutes: Int,
  lookbackMinutes: Int
)

/**
 * MongoDB configuration.
 *
 * @param uri      connection string (mongodb://localhost:27017 for local)
 * @param database database name
 */
case class MongoConfig(
  uri: String,
  database: String
)

/**
 * Groq API configuration for AI summary generation.
 *
 * Groq is a fast inference provider for open-source LLMs (Llama, Mixtral).
 * Free tier, get key at https://console.groq.com/keys
 *
 * @param apiKey   API key (required via env GROQ_API_KEY)
 * @param endpoint API URL (default: OpenAI-compatible endpoint)
 * @param model    LLM model (default: llama-3.3-70b-versatile)
 */
case class GroqConfig(
  apiKey: String,
  endpoint: String,
  model: String
)

/**
 * Root application configuration object.
 *
 * Loaded from application.conf via PureConfig.
 * Environment variables override file values
 * (priority: env vars > application.conf > defaults).
 */
case class Config(
  bot: BotConfig,
  summary: SummaryConfig,
  mongodb: MongoConfig,
  groq: GroqConfig
) {
  // Base Telegram Bot API URL (does not change)
  val apiUrl: String = "https://api.telegram.org"
}

object Config {

  /**
   * Load configuration from application.conf with env var overrides.
   *
   * Priority order:
   * 1. Environment variables (TELEGRAM_BOT_TOKEN, TARGET_CHAT_ID, GROQ_API_KEY, etc.)
   * 2. Values from application.conf
   *
   * After loading — validates required fields.
   *
   * @return Right(Config) on success, Left(error) on failure
   */
  def load(): Either[String, Config] = {
    // Load base config from application.conf via PureConfig
    val baseConfig = ConfigSource.default.load[Config]

    // Override values from env vars (if specified)
    val withEnvOverrides = baseConfig match {
      case Right(config) =>
        // sys.env.getOrElse — if variable doesn't exist, use value from config
        val botToken = sys.env.getOrElse("TELEGRAM_BOT_TOKEN", config.bot.token)
        val targetChatId = sys.env.get("TARGET_CHAT_ID").flatMap(_.toLongOption).getOrElse(config.bot.targetChatId)
        val groqApiKey = sys.env.getOrElse("GROQ_API_KEY", config.groq.apiKey)
        val groqEndpoint = sys.env.getOrElse("GROQ_API_ENDPOINT", config.groq.endpoint)
        val groqModel = sys.env.getOrElse("GROQ_MODEL", config.groq.model)

        val bot = config.bot.copy(token = botToken, targetChatId = targetChatId)
        val groq = config.groq.copy(apiKey = groqApiKey, endpoint = groqEndpoint, model = groqModel)
        Right(config.copy(bot = bot, groq = groq))

      case Left(failures) =>
        Left(failures)
    }

    // Validation: check that required fields are populated
    withEnvOverrides match {
      case Right(config) =>
        // Validation
        if (config.bot.token.isEmpty) {
          Left("TELEGRAM_BOT_TOKEN not set (neither in config nor in environment variables)")
        } else if (config.groq.apiKey.isEmpty) {
          Left("GROQ_API_KEY not set (neither in config nor in environment variables)")
        } else {
          Right(config)
        }

      case Left(failures: ConfigReaderFailures) =>
        Left(s"Config loading error: ${failures.toList.mkString(", ")}")
    }
  }

  /**
   * Extension methods for convenient access to config fields.
   *
   * Allows writing config.botToken instead of config.bot.token
   */
  implicit class ConfigOps(c: Config) {
    def botToken: String = c.bot.token
    def targetChatId: Long = c.bot.targetChatId
    def summaryIntervalMinutes: Int = c.summary.intervalMinutes
    def lookbackMinutes: Int = c.summary.lookbackMinutes
    def mongodbUri: String = c.mongodb.uri
    def mongodbDatabase: String = c.mongodb.database
    def groqApiKey: String = c.groq.apiKey
    def groqEndpoint: String = c.groq.endpoint
    def groqModel: String = c.groq.model
  }
}