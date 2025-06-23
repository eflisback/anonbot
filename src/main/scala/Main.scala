import net.dv8tion.jda.api.{JDABuilder, JDA}
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.interactions.commands.build.Commands

import discord.Anonbot
import config.BotConfig
import utils.{Logger, LogLevel}
import scala.util.{Try, Success, Failure}

object Main:

  @main def run(): Unit =
    BotConfig.load() match
      case Success(config) =>
        initializeBot(config) match
          case Success((jda, anonbot)) =>
            registerCommands(jda)
            runBot(jda, anonbot)
          case Failure(exception) =>
            Logger.errorWithException(s"Failed to initialize bot", exception)
            sys.exit(1)
      case Failure(exception) =>
        Logger.errorWithException("Failed to load configuration", exception)
        sys.exit(1)

  private def initializeBot(config: BotConfig): Try[(JDA, Anonbot)] =
    scala.util.Try {
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

      (jda, anonbot)
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

  private def runBot(jda: JDA, anonbot: Anonbot): Unit =
    try
      jda.awaitReady()
      Logger.info("Bot is now running! Press Ctrl+C to stop.")

      sys.addShutdownHook {
        Logger.info("Shutting down gracefully...")
        anonbot.cleanup()
        jda.shutdown()
      }

      Thread.currentThread().join()
    catch
      case _: InterruptedException =>
        Logger.warning("Bot interrupted, shutting down...")
        anonbot.cleanup()
        jda.shutdown()
