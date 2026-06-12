package clients

import cats.effect.IO
import cats.syntax.all._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import config.Config
import config.Config.ConfigOps
import dto.{GetUpdatesResponse, TelegramMessage, Update}
import model.Message
import repository.MessageRepository

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import scala.concurrent.duration._

/**
 * Telegram Bot API client.
 *
 * Responsible for:
 * - Long Polling: continuously receiving new messages from the chat
 * - Saving each message to MongoDB via repository
 * - Sending messages back to the chat (summaries, welcome, etc.)
 *
 * Dependencies are passed via constructor (constructor injection).
 * All side-effects (network, time) are wrapped in IO.
 *
 * Uses JDK HttpClient (not Akka HTTP, not sttp) for minimal dependencies.
 */
class TelegramClient(config: Config, repo: MessageRepository[IO]) {

  // JDK HttpClient — built into Java 11+, no third-party libraries needed
  private val httpClient: HttpClient = HttpClient.newHttpClient()

  // Jackson for parsing JSON responses from Telegram API.
  // DefaultScalaModule adds support for Scala types (case class, Option, Seq)
  private val objectMapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)

  // Listener working flag. @volatile — visible from other threads without caching
  @volatile private var running: Boolean = true

  // Offset for Long Polling — Telegram returns only updates with update_id >= offset.
  // Increment after each batch to avoid getting the same message twice
  @volatile private var offset: Int = 0

  /**
   * Start Long Polling to receive updates.
   *
   * Long Polling works as follows:
   * 1. Send GET /getUpdates?offset=X&timeout=30
   * 2. Telegram waits up to 30 seconds for a new message
   * 3. Receive list of Updates (each contains a message)
   * 4. Filter by targetChatId, convert to domain model, save to MongoDB
   * 5. Update offset to avoid getting the same messages again
   * 6. Repeat
   */
  def startListening(): IO[Unit] = {

    /**
     * Process a single update from Telegram.
     *
     * Logic:
     * - If message has text and is from our chat → save to MongoDB
     * - If message is from another chat → ignore
     * - If not a message (callback, bot command, etc.) → ignore
     */
    def processUpdate(update: Update): IO[Unit] = {
      update.message match {
        case Some(msg: TelegramMessage) if msg.text.isDefined && msg.chat.id == config.targetChatId =>
          Message.fromTelegramMessage(msg) match {
            case Some(domainMessage) =>
              repo.create(domainMessage) >>
                IO.println(s"[${msg.from.map(_.firstName).getOrElse("Unknown")}] ${msg.text.get}")
            case None =>
              IO.unit
          }
        case Some(msg) if msg.chat.id != config.targetChatId =>
          IO.unit // Ignore messages from other chats
        case _ => IO.unit
      }
    }

    /**
     * Make one HTTP request to Telegram getUpdates.
     *
     * timeout=30 — Telegram server will wait up to 30 seconds before
     * returning an empty response. This reduces API load (no spamming requests).
     */
    def fetchUpdates: IO[GetUpdatesResponse] = IO {
      val url = s"${config.apiUrl}/bot${config.botToken}/getUpdates?offset=$offset&timeout=30"
      val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build()

      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      val json = response.body()

      if (response.statusCode() != 200) {
        throw new RuntimeException(s"Telegram API error: ${response.statusCode()} - $json")
      }

      objectMapper.readValue(json, classOf[GetUpdatesResponse])
    }

    /**
     * Main Long Polling loop.
     *
     * 1 second pause between requests — to avoid spamming Telegram API
     * on errors or empty responses.
     */
    def loop: IO[Unit] = {
      for {
        _ <- IO.sleep(1.second)
        response <- fetchUpdates.handleErrorWith { err =>
          IO.println(s"Error fetching updates: $err") >> IO.pure(GetUpdatesResponse(false, Nil))
        }

        _ <- if (response.ok && response.updates.nonEmpty) {
          val lastOffset = response.updates.map(_.updateId).max
          IO { offset = lastOffset + 1 } >>
            response.updates.filter(_.message.isDefined).traverse(processUpdate)
        } else {
          IO.unit
        }

        _ <- if (running) loop else IO.unit
      } yield ()
    }

    IO.println("🤖 Starting Long Polling...") >> loop
  }

  /**
   * Send a text message to the chat (plain text, no formatting).
   */
  def sendMessage(text: String): IO[Unit] = sendRawMessage(text, None)

  /**
   * Send a message with formatting (default: MarkdownV2).
   *
   * MarkdownV2 requires escaping special characters (_, *, [, ], etc.)
   * — see Summary.escapeMarkdown()
   */
  def sendMessageFormatted(text: String, parseMode: String = "MarkdownV2"): IO[Unit] =
    sendRawMessage(text, Some(parseMode))

  /**
   * Reply to a specific message.
   * Used when needing to reply to a user command.
   */
  def replyToMessage(messageId: Int, text: String): IO[Unit] = sendRawMessage(text, None, Some(messageId))

  /**
   * Get bot information (token verification).
   *
   * Called at application startup to ensure the token is valid
   * and the bot exists. If token is invalid — throws exception and app crashes.
   */
  def getMe(): IO[BotInfo] = IO {
    val url = s"${config.apiUrl}/bot${config.botToken}/getMe"
    val request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .GET()
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    val json = response.body()

    val node = objectMapper.readTree(json)
    val result = node.get("result")

    BotInfo(
      id = result.get("id").asLong(),
      username = result.get("username").asText()
    )
  }

  /**
   * Stop the listener (graceful shutdown).
   * Sets running = false, and the loop will complete on the next iteration.
   */
  def stop(): IO[Unit] = IO {
    running = false
    println("🛑 Long polling stopped")
  }

  /**
   * Send a welcome message when the bot starts.
   * Users in the chat will see that the bot is running.
   */
  def sendWelcomeMessage(): IO[Unit] = {
    val welcome =
      """
        |🤖 *Hustlers Ljubljana Bot* is running\!
        |
        |I will monitor the chat and create discussion summaries\.
        """.stripMargin
    sendMessageFormatted(welcome)
  }

  /**
   * Internal method for sending a message via HTTP POST.
   *
   * Builds JSON body manually (no serialization libraries) —
   * this is a simple request with 2-3 fields, so Jackson is redundant here.
   *
   * @param text             message text
   * @param parseMode        formatting mode (None = plain text, Some("MarkdownV2"), etc.)
   * @param replyToMessageId if specified — reply to a specific message
   */
  private def sendRawMessage(text: String, parseMode: Option[String] = None, replyToMessageId: Option[Int] = None): IO[Unit] = IO {
    val url = s"${config.apiUrl}/bot${config.botToken}/sendMessage"

    val json = parseMode match {
      case Some(mode) if replyToMessageId.isDefined =>
        s"""{"chat_id":"${config.targetChatId}","text":"${escapeJson(text)}","parse_mode":"$mode","reply_to_message_id":${replyToMessageId.get}}"""
      case Some(mode) =>
        s"""{"chat_id":"${config.targetChatId}","text":"${escapeJson(text)}","parse_mode":"$mode"}"""
      case None if replyToMessageId.isDefined =>
        s"""{"chat_id":"${config.targetChatId}","text":"${escapeJson(text)}","reply_to_message_id":${replyToMessageId.get}}"""
      case None =>
        s"""{"chat_id":"${config.targetChatId}","text":"${escapeJson(text)}"}"""
    }

    val request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

    if (response.statusCode() != 200) {
      throw new RuntimeException(s"Error sending message: ${response.statusCode()} - ${response.body()}")
    }
  }

  /**
   * Escape special characters for JSON strings.
   *
   * Required because we build JSON manually via string interpolation.
   * Without escaping, characters like \n or " would break the JSON.
   */
  private def escapeJson(text: String): String = {
    text
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
  }
}

/**
 * Bot information (response from getMe).
 */
case class BotInfo(id: Long, username: String)