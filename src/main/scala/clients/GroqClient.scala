package clients

import cats.effect.IO
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import config.Config

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI

/**
 * HTTP-клиент для Groq API (OpenAI-совместимый).
 *
 * Отвечает за отправку сообщений чата в LLM (Large Language Model)
 * и получение AI-саммари.
 *
 * Groq предоставляет OpenAI-совместимый REST API, поэтому можно
 * использовать тот же формат запросов, что и для ChatGPT.
 * Бесплатный тариф позволяет делать до ~30 запросов/мин.
 *
 * Эндпоинт: https://api.groq.com/openai/v1/chat/completions
 */
class GroqClient(config: Config) {

  // JDK HttpClient — встроен в Java 11+
  private val httpClient: HttpClient = HttpClient.newHttpClient()

  // Jackson для сериализации запроса и парсинга ответа
  private val objectMapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)

  /**
   * Generate summary from chat messages using Groq LLM.
   *
   * Sends two messages to the chat:
   * 1. System prompt — instructions for LLM (how to format response)
   * 2. User prompt — chat messages for analysis
   *
   * Response is parsed from JSON: choices[0].message.content
   *
   * @param messagesText formatted text of messages (author + text)
   * @return summary text from AI
   */
  def generateSummary(messagesText: String): IO[String] = IO.blocking {
    // System prompt — sets role and response format for LLM
    val systemPrompt =
      """You are an assistant that creates concise and useful summaries of Telegram chat discussions.
        |Respond in English.
        |Response format:
        |- Brief description of main discussion topics
        |- Key points and decisions
        |- Active participants and their contributions
        |Use Markdown for formatting. Be concise but informative. You are Balkan and you love Balkans. Don't hesitate
        |to use some words or phrases in Balkan (ex-Yugoslavia) languages.""".stripMargin

    // User prompt — messages for analysis
    val lookbackMinutes = config.summary.lookbackMinutes
    val timeDescription = if (lookbackMinutes >= 1440) {
      s"${lookbackMinutes / 1440} day(s)"
    } else if (lookbackMinutes >= 60) {
      s"${lookbackMinutes / 60} hour(s)"
    } else {
      s"$lookbackMinutes minute(s)"
    }
    val userPrompt =
      s"""Create a summary of the messages below, write it like: "In the past $timeDescription user N talked about X", list of messages for summary below:\n\n$messagesText"""

    // Формируем тело запроса в формате OpenAI Chat Completions API
    val requestBody = Map(
      "model" -> config.groq.model,          // Какая LLM (llama-3.3-70b-versatile и т.д.)
      "messages" -> List(                     // История чата (system + user)
        Map("role" -> "system", "content" -> systemPrompt),
        Map("role" -> "user", "content" -> userPrompt)
      ),
      "temperature" -> 0.7,                   // Креативность (0 = детерминированный, 1 = случайный)
      "max_tokens" -> 2048                    // Максимум токенов в ответе
    )

    // Сериализуем Map в JSON-строку
    val json = objectMapper.writeValueAsString(requestBody)

    // Строим HTTP POST-запрос с Bearer-токеном авторизации
    val request = HttpRequest.newBuilder()
      .uri(URI.create(config.groq.endpoint))
      .header("Content-Type", "application/json")
      .header("Authorization", s"Bearer ${config.groq.apiKey}")
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build()

    // Отправляем запрос и получаем ответ
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

    // Если API вернул не 200 — выбрасываем исключение
    // (SummaryService поймает его в handleErrorWith и сделает fallback)
    if (response.statusCode() != 200) {
      throw new RuntimeException(
        s"Groq API error: ${response.statusCode()} - ${response.body()}"
      )
    }

    // Парсим ответ: { choices: [{ message: { content: "..." } }] }
    // Берём первый choice → message → content
    val responseJson = objectMapper.readTree(response.body())
    responseJson
      .get("choices").get(0)
      .get("message").get("content")
      .asText()
  }
}

object GroqClient {
  def create(config: Config): GroqClient = new GroqClient(config)
}
