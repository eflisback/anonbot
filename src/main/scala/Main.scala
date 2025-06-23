import net.dv8tion.jda.api.{JDABuilder, JDA}
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.OptionType

import scala.util.{Try, Success, Failure}

import discord.Anonbot
import config.BotConfig
import utils.Logger

object Main:

  @main def run(): Unit =
    BotConfig.load() match
      case Success(config) =>
        initializeBot(config) match
          case Success(jda) =>
            Anonbot.initialize(config)
            registerCommands(jda)
            runBot(jda)
          case Failure(exception) =>
            Logger.error(s"Failed to initialize bot: ${exception.getMessage}")
            sys.exit(1)
      case Failure(exception) =>
        Logger.error(exception.getMessage)
        sys.exit(1)

  private def initializeBot(config: BotConfig): Try[JDA] =
    scala.util.Try {
      JDABuilder
        .createLight(
          config.token,
          GatewayIntent.GUILD_MESSAGES,
          GatewayIntent.DIRECT_MESSAGES,
          GatewayIntent.MESSAGE_CONTENT,
          GatewayIntent.DIRECT_MESSAGE_REACTIONS
        )
        .addEventListeners(Anonbot)
        .build()
    }

  private def registerCommands(jda: JDA): Unit =
    jda
      .updateCommands()
      .addCommands(
        Commands.slash("manual", "Visa användarmanualen")
      )
      .queue(
        _ => Logger.success("Commands registered successfully"),
        error => Logger.errorWithException("Failed to register commands", error)
      )

  private def runBot(jda: JDA): Unit =
    try
      jda.awaitReady()
      Logger.info("Bot is now running! Press Ctrl+C to stop.")

      sys.addShutdownHook {
        Logger.info("Shutting down gracefully...")
        Anonbot.cleanup()
        jda.shutdown()
      }

      Thread.currentThread().join()
    catch
      case _: InterruptedException =>
        Logger.warning("Bot interrupted, shutting down...")
        Anonbot.cleanup()
        jda.shutdown()
