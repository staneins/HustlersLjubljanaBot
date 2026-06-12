# Hustlers Ljubljana Bot

Telegram bot for automatic summarization of chat discussions.

## Features

1. **Reads messages** from a specified Telegram chat via Long Polling
2. **Stores** them in MongoDB (text, author, timestamp)
3. **Generates AI summaries** via Groq API (Llama 3.3 70B model)
4. **Sends summaries** back to the chat on a schedule

## Quick Start

### 1. Start MongoDB

```bash
docker run -d -p 27017:27017 --name tg_summary_mongo mongo:latest
```

### 2. Configure Environment Variables

```bash
export TELEGRAM_BOT_TOKEN="your_bot_token_from_botfather"
export TARGET_CHAT_ID="-1001234567890"
export GROQ_API_KEY="gsk_your_api_key"
```

### 3. Run the Bot

```bash
sbt run
```

## Configuration

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `TELEGRAM_BOT_TOKEN` | Bot token from [@BotFather](https://t.me/BotFather) | ✅ Yes |
| `TARGET_CHAT_ID` | Chat ID to monitor | ✅ Yes |
| `GROQ_API_KEY` | API key from [Groq Cloud](https://console.groq.com/keys) | ✅ Yes |
| `GROQ_API_ENDPOINT` | Groq API endpoint | ❌ No (default: `https://api.groq.com/openai/v1/chat/completions`) |
| `GROQ_MODEL` | LLM model | ❌ No (default: `llama-3.3-70b-versatile`) |

### application.conf

Configuration file: `src/main/resources/application.conf`

```hocon
bot {
  token = "..."           # overridden by TELEGRAM_BOT_TOKEN
  target-chat-id = ...    # overridden by TARGET_CHAT_ID
}

summary {
  interval-minutes = 5    # how often to generate summaries
  lookback-minutes = 1440 # time period for messages (1440 = 24 hours)
}

mongodb {
  uri = "mongodb://localhost:27017"
  database = "tg_summary"
}

groq {
  api-key = "..."    # overridden by GROQ_API_KEY
  endpoint = "..."
  model = "llama-3.3-70b-versatile"
}
```

## How to Get TARGET_CHAT_ID

1. Add the bot to your chat
2. Send any message in the chat
3. Check bot logs — the chat ID will be printed there
4. Or use [@getmyid_bot](https://t.me/getmyid_bot)

## Architecture

```
src/main/scala/
├── Main.scala                    # Entry point (IOApp)
├── BotApp.scala                  # Component assembly (manual DI)
├── config/
│   └── Config.scala              # Config loading (pureconfig + env vars)
├── model/
│   └── Message.scala             # Domain model for messages
├── dto/
│   ├── MessageDto.scala          # DTOs for Telegram API
│   └── SummaryDto.scala          # Summary model
├── clients/
│   ├── TelegramClient.scala      # HTTP client for Telegram Bot API
│   └── GroqClient.scala          # HTTP client for Groq API
├── repository/
│   ├── MessageRepository.scala   # Repository trait
│   └── MessageRepositoryImpl.scala # MongoDB implementation
├── service/
│   └── SummaryService.scala      # AI summary generation
└── scheduler/
    └── SummaryScheduler.scala    # Periodic job scheduler
```

## Data Flow

```
[Telegram Chat]
    ↓ (HTTP Long Polling /getUpdates)
[TelegramClient]
    ↓ (save: text, author, timestamp)
[MessageRepository → MongoDB]
    ↓ (every N minutes on schedule)
[SummaryScheduler] → [SummaryService]
    ↓ (format messages)
[GroqClient → Groq API (LLM)]
    ↓ (AI summary)
[TelegramClient] → (HTTP POST /sendMessage)
    ↓
[Telegram Chat]
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Language** | Scala 2.13 |
| **Effects** | Cats Effect 3 |
| **Database** | MongoDB (sync driver) |
| **JSON** | Jackson |
| **Config** | PureConfig |
| **HTTP** | JDK HttpClient (Java 11+) |
| **Logging** | Logback |
| **Testing** | MUnit |

## Testing

```bash
sbt test
```

### Test Coverage

- `MessageSuite` — Telegram DTO to domain model conversion
- `SummarySuite` — Statistics generation, markdown escaping
- `ConfigSuite` — Configuration loading
- `TelegramClientSuite` — JSON escaping
- `SummaryServiceSuite` — Summary generation with mocks

## Sample Summary

```
📊 Chat Summary

Period: 12.06.2026 13:00 — 13.06.2026 13:00
Total messages: 156
Participants: 12

🥇 Alice: 45
🥈 Bob: 32
🥉 Charlie: 28

Top Participants:
• Alice: 45
• Bob: 32
• Charlie: 28

[AI-generated discussion summary...]
```

## Production

### Docker Compose (optional)

```yaml
version: '3.8'
services:
  mongo:
    image: mongo:latest
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db

  bot:
    build: .
    environment:
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - TARGET_CHAT_ID=${TARGET_CHAT_ID}
      - GROQ_API_KEY=${GROQ_API_KEY}
    depends_on:
      - mongo

volumes:
  mongo_data:
```

### Logging

Logs are written to:
- Console (stdout)
- File: `logs/bot.log`

Logging configuration: `src/main/resources/logback.xml`

## Development

### Build

```bash
sbt compile
```

### Run in Watch Mode

```bash
sbt ~run
```

### Code Formatting

The project follows standard Scala conventions. It's recommended to configure scalafmt in your IDE.

## License

MIT