package discord

import net.dv8tion.jda
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

object Anonbot extends ListenerAdapter:
  override def onSlashCommandInteraction(
      event: SlashCommandInteractionEvent
  ): Unit =
    event.getName match
      case "ask"  => onAskCommandInteraction(event)
      case "help" => onHelpCommandInteraction(event)
      case _ =>
        event
          .reply("Unknown command. Use `/help` for options.")
          .setEphemeral(true)
          .queue()

  private def onAskCommandInteraction(
      event: SlashCommandInteractionEvent
  ): Unit =
    println("Ggmm")

  private def onHelpCommandInteraction(
      event: SlashCommandInteractionEvent
  ): Unit =
    println("hmhmmh")
