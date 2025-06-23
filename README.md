# Anonymous Questions Discord Bot 

Discord bot which repeats privately asked questions in a target text channel on a target Discord server. Made using Scala 3 and [JDA](https://jda.wiki/introduction/jda/).

## Features

- Receive questions via direct messages to the bot
- Show preview and require confirmation before posting
- Post questions anonymously to a specific Discord channel
- Automatic cleanup of expired pending questions
- Configurable timeouts and message limits

## Local development

### Environment configuration

In order for the bot to work, a few environment variables must be set.

#### Required Environment Variables

| Variable           | Description                                                  | Example                          |
| ------------------ | ------------------------------------------------------------ | -------------------------------- |
| `DISCORD_TOKEN`    | Your Discord bot token                                       | `MTIzNDU2Nzg5MDEyMzQ1Njc4OTA...` |
| `DISCORD_GUILD_ID` | The Discord server (guild) ID where questions will be posted | `123456789012345678`             |

#### Optional Environment Variables

| Variable                       | Description                                   | Default         | Example               |
| ------------------------------ | --------------------------------------------- | --------------- | --------------------- |
| `DISCORD_CHANNEL_NAME`         | Name of the text channel to post questions to | `fråga-anonymt` | `anonymous-questions` |
| `CONFIRMATION_TIMEOUT_MINUTES` | Minutes before pending questions expire       | `10`            | `15`                  |
| `MAX_QUESTION_LENGTH`          | Maximum character limit for questions         | `10000`         | `5000`                |
| `ENVIRONMENT`                  | Set to `development` for debug logging        | (none)          | `development`         |

#### Getting the Discord variable values

Discord Bot Token
1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application or select existing one
3. Go to "Bot" section
4. Copy the token (keep this secret!)

Discord Guild ID
1. Enable Developer Mode in Discord (User Settings → Advanced → Developer Mode)
2. Right-click on your server name
3. Select "Copy Server ID"

#### Setting Environment Variables

**Windows (Command Prompt):**
```cmd
set DISCORD_TOKEN=your_bot_token_here
set DISCORD_GUILD_ID=your_guild_id_here
set DISCORD_CHANNEL_NAME=fråga-anonymt
```

**Windows (PowerShell):**
```powershell
$env:DISCORD_TOKEN="your_bot_token_here"
$env:DISCORD_GUILD_ID="your_guild_id_here"
$env:DISCORD_CHANNEL_NAME="fråga-anonymt"
```

**macOS/Linux (Bash/Zsh):**
```bash
export DISCORD_TOKEN="your_bot_token_here"
export DISCORD_GUILD_ID="your_guild_id_here"
export DISCORD_CHANNEL_NAME="fråga-anonymt"
```

### SBT

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

For more information on the sbt-dotty plugin, see the
[scala3-example-project](https://github.com/scala/scala3-example-project/blob/main/README.md).

## Usage

1. Set up the required environment variables
2. Create a text channel named `fråga-anonymt` (or your custom channel name) in your Discord server
3. Invite the bot to your server with appropriate permissions:
   - Send Messages
   - Use Slash Commands
   - Add Reactions
   - Read Message History
4. Run the bot with `sbt run`
5. Users can now send direct messages to the bot to ask anonymous questions

## Bot Permissions

The bot needs the following Discord permissions:
- **Send Messages** - To post anonymous questions
- **Use Slash Commands** - For the `/manual` command
- **Add Reactions** - To add confirmation reactions
- **Read Message History** - To process reactions on confirmation messages
