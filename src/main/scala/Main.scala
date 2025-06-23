import net.dv8tion.jda.api.{JDABuilder, JDA}
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.OptionType

import discord.Anonbot

object Main:

  @main def run(): Unit =
    val token = getTokenOrFail()
    val jda = initializeBot(token)

    registerCommands(jda)
    runBot(jda)

  private def getTokenOrFail(): String =
    val RequiredTokenEnv = "DISCORD_TOKEN"

    sys.env.getOrElse(
      RequiredTokenEnv,
      throw IllegalStateException(
        s"Missing $RequiredTokenEnv environment variable! See README.md for setup."
      )
    )

  private def initializeBot(token: String) =
    JDABuilder
      .createLight(
        token,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.DIRECT_MESSAGES,
        GatewayIntent.MESSAGE_CONTENT,
        GatewayIntent.DIRECT_MESSAGE_REACTIONS
      )
      .addEventListeners(Anonbot)
      .build()

  private def registerCommands(jda: JDA): Unit =
    jda
      .updateCommands()
      .addCommands(
        Commands.slash("help", "Show help message")
      )
      .queue()

  private def runBot(jda: JDA): Unit =
    try
      jda.awaitReady()
      println("Bot is now running! Press Ctrl+C to stop.")

      sys.addShutdownHook {
        println("Shutting down gracefully...")
        Anonbot.cleanup()
        jda.shutdown()
      }

      Thread.currentThread().join()
    catch
      case _: InterruptedException =>
        println("Bot interrupted, shutting down...")
        Anonbot.cleanup()
        jda.shutdown()
