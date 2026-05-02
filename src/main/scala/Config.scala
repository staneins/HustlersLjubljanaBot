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
  implicit class ConfigOps(c: Config) {
    def botToken: String = c.bot.token
    def targetChatId: Long = c.bot.targetChatId
    def summaryIntervalMinutes: Int = c.summary.intervalMinutes
    def lookbackMinutes: Int = c.summary.lookbackMinutes
    def mongodbUri: String = c.mongodb.uri
    def mongodbDatabase: String = c.mongodb.database
  }
}