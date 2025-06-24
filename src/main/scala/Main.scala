// External imports
import net.dv8tion.jda.api.{JDABuilder, JDA}
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.interactions.commands.build.Commands

// Internal imports
import discord.Anonbot
import config.BotConfig
import utils.{Logger, LogLevel}

// Scala imports
import scala.util.{Try, Success, Failure}

@main def run(): Unit =
  BotConfig
    .load()
    .map(instantiateBot)
    .recover(exception =>
      Logger.errorWithException("Failed to load configuration", exception)
      sys.exit(1)
    )

def instantiateBot(config: BotConfig): Unit =
  Try {
    val anonbot = new Anonbot(config)
    val jda = JDABuilder
      .createLight(
        config.token,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.DIRECT_MESSAGES,
        GatewayIntent.MESSAGE_CONTENT,
        GatewayIntent.DIRECT_MESSAGE_REACTIONS
      )
      .addEventListeners(anonbot)
      .build()

    (anonbot, jda)
  }.map(startBot)
    .recover(exception =>
      Logger.errorWithException("Failed to instantiate bot", exception)
      sys.exit(1)
    )

def startBot(anonbot: Anonbot, jda: JDA): Unit =
  registerCommands(jda)

  Try {
    jda.awaitReady()
    Logger.info("Bot is now running! Press Ctrl+C to stop.")

    sys.addShutdownHook { stopBot(anonbot, jda) }

    Thread.currentThread().join()
  }.recover {
    case _: InterruptedException =>
      Logger.warning("Bot interrupted")
      stopBot(anonbot, jda)
    case exception =>
      Logger.errorWithException("Bot runtime error", exception)
      stopBot(anonbot, jda)
  }

def registerCommands(jda: JDA): Unit =
  jda
    .updateCommands()
    .addCommands(
      Commands.slash("manual", "Visa användarmanualen")
    )
    .queue(
      _ => Logger.success("Commands registered successfully"),
      error => Logger.errorWithException("Failed to register commands", error)
    )

def stopBot(anonbot: Anonbot, jda: JDA): Unit =
  Logger.info("Shutting down gracefully...")
  anonbot.cleanup()
  jda.shutdown()
