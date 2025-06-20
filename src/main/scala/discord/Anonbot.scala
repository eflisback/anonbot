package discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.entities.Guild
import scala.jdk.CollectionConverters._

object Anonbot extends ListenerAdapter:

  override def onSlashCommandInteraction(
      event: SlashCommandInteractionEvent
  ): Unit =
    event.getName match
      case "ask"  => handleAskCommand(event)
      case "help" => handleHelpCommand(event)
      case _      => sendUnknownCommandReply(event)

  private def handleAskCommand(event: SlashCommandInteractionEvent): Unit =
    val questionOption = event.getOption("question")

    questionOption match
      case null =>
        event
          .reply("Please provide a question!")
          .setEphemeral(true)
          .queue()

      case question =>
        event
          .reply("Your question has been submitted anonymously!")
          .setEphemeral(true)
          .queue()

        broadcastQuestion(event.getJDA, question.getAsString)

  private def handleHelpCommand(event: SlashCommandInteractionEvent): Unit =
    val helpMessage = """
      |**Anonymous Question Bot Help**
      |`/ask <question>` - Submit a question anonymously
      |`/help` - Show this help message
      """.stripMargin

    event
      .reply(helpMessage)
      .setEphemeral(true)
      .queue()

  private def sendUnknownCommandReply(
      event: SlashCommandInteractionEvent
  ): Unit =
    event
      .reply("Unknown command. Use `/help` for options.")
      .setEphemeral(true)
      .queue()

  private def broadcastQuestion(jda: JDA, question: String): Unit =
    val guilds: List[Guild] = jda.getGuilds.asScala.toList

    if guilds.isEmpty then println("Warning: Bot is not in any guilds!")
    else
      val announcement = s"**Anonymous Question:**\n$question"

      guilds.foreach(guild =>
        guild.getDefaultChannel match
          case null =>
            println(s"Couldn't find a default channel in ${guild.getName}")
          case channel =>
            channel.asTextChannel.sendMessage(announcement).queue()
      )
