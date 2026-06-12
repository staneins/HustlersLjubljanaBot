package config

import pureconfig.{ConfigSource, ConfigReader}
import pureconfig.generic.auto._
import pureconfig.error.ConfigReaderFailures

/**
 * Конфигурация Telegram-бота.
 *
 * @param token        токен бота от @BotFather (обязательно через env TELEGRAM_BOT_TOKEN)
 * @param targetChatId ID чата, за которым следим (обязательно через env TARGET_CHAT_ID)
 */
case class BotConfig(
  token: String,
  targetChatId: Long
)

/**
 * Конфигурация генерации саммари.
 *
 * @param intervalMinutes  интервал между генерацией саммари (по умолчанию 1440 = 24 часа)
 * @param lookbackMinutes  за сколько минут назад собирать сообщения (по умолчанию 1440 = 24 часа)
 */
case class SummaryConfig(
  intervalMinutes: Int,
  lookbackMinutes: Int
)

/**
 * Конфигурация MongoDB.
 *
 * @param uri      строка подключения (mongodb://localhost:27017 для локальной)
 * @param database имя базы данных
 */
case class MongoConfig(
  uri: String,
  database: String
)

/**
 * Конфигурация Groq API для AI-генерации саммари.
 *
 * Groq — это быстрый инференс для open-source LLM (Llama, Mixtral).
 * Бесплатный тариф, ключ получить на https://console.groq.com/keys
 *
 * @param apiKey   API-ключ (обязательно через env GROQ_API_KEY)
 * @param endpoint URL API (дефолт: OpenAI-совместимый эндпоинт)
 * @param model    модель LLM (дефолт: llama-3.3-70b-versatile)
 */
case class GroqConfig(
  apiKey: String,
  endpoint: String,
  model: String
)

/**
 * Корневой объект конфигурации приложения.
 *
 * Загружается из application.conf через PureConfig.
 * Переменные окружения переопределяют значения из файла
 * (приоритет: env vars > application.conf > дефолты).
 */
case class Config(
  bot: BotConfig,
  summary: SummaryConfig,
  mongodb: MongoConfig,
  groq: GroqConfig
) {
  // Базовый URL Telegram Bot API (не меняется)
  val apiUrl: String = "https://api.telegram.org"
}

object Config {

  /**
   * Загрузить конфигурацию из application.conf с переопределением через env vars.
   *
   * Порядок приоритетов:
   * 1. Переменные окружения (TELEGRAM_BOT_TOKEN, TARGET_CHAT_ID, GROQ_API_KEY и т.д.)
   * 2. Значения из application.conf
   *
   * После загрузки — валидация обязательных полей.
   *
   * @return Right(Config) при успехе, Left(ошибка) при проблемах
   */
  def load(): Either[String, Config] = {
    // Загружаем базовый конфиг из application.conf через PureConfig
    val baseConfig = ConfigSource.default.load[Config]

    // Переопределяем значения из env vars (если заданы)
    val withEnvOverrides = baseConfig match {
      case Right(config) =>
        // sys.env.getOrElse — если переменной нет, берём значение из конфига
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

    // Валидация: проверяем, что обязательные поля заполнены
    withEnvOverrides match {
      case Right(config) =>
        // Валидация
        if (config.bot.token.isEmpty) {
          Left("TELEGRAM_BOT_TOKEN не задан (ни в конфиге, ни в переменных окружения)")
        } else if (config.bot.targetChatId <= 0) {
          Left("TARGET_CHAT_ID должен быть положительным числом")
        } else if (config.groq.apiKey.isEmpty) {
          Left("GROQ_API_KEY не задан (ни в конфиге, ни в переменных окружения)")
        } else {
          Right(config)
        }

      case Left(failures: ConfigReaderFailures) =>
        Left(s"Ошибка загрузки конфига: ${failures.toList.mkString(", ")}")
    }
  }

  /**
   * Extension-методы для удобного доступа к полям конфига.
   *
   * Позволяет писать config.botToken вместо config.bot.token
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