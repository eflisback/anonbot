import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import java.util.Collections
import discord.Anonbot

object Main:

  @main def run(): Unit =
    val token = sys.env.getOrElse(
      "DISCORD_TOKEN",
      throw new IllegalStateException(
        "Missing DISCORD_TOKEN environment variable! See README.md for setup."
      )
    )

    JDABuilder
      .createLight(token)
      .addEventListeners(Anonbot)
      .build()