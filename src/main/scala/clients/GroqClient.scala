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
   * Сгенерировать саммари из списка сообщений чата с помощью Groq LLM.
   *
   * Отправляет два сообщения в чат:
   * 1. System prompt — инструкции для LLM (как форматировать ответ)
   * 2. User prompt — сами сообщения чата для анализа
   *
   * Ответ парсится из JSON: choices[0].message.content
   *
   * @param messagesText отформатированный текст сообщений (автор + текст)
   * @return текст саммари от AI
   */
  def generateSummary(messagesText: String): IO[String] = IO.blocking {
    // System prompt — задаёт роль и формат ответа для LLM
    val systemPrompt =
      """Ты — ассистент, который делает краткие и полезные саммари обсуждений в Telegram-чате.
        |Отвечай на русском языке.
        |Формат ответа:
        |- Краткое описание основных тем обсуждений
        |- Ключевые моменты и решения
        |- Активные участники и их вклад
        |Используй Markdown для форматирования. Будь лаконичен, но информативен.""".stripMargin

    // User prompt — сами сообщения для анализа
    val lookbackMinutes = config.summary.lookbackMinutes
    val timeDescription = if (lookbackMinutes >= 1440) {
      s"${lookbackMinutes / 1440} дн."
    } else if (lookbackMinutes >= 60) {
      s"${lookbackMinutes / 60} ч."
    } else {
      s"$lookbackMinutes мин."
    }
    val userPrompt =
      s"""Сделай саммари по сообщениям ниже, напиши так: "За прошедшее $timeDescription пользователь N говорил о том-то", список сообщений для саммари ниже:\n\n$messagesText"""

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
