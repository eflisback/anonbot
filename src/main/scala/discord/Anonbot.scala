package discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.entities.{Guild, Message, User}
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import scala.jdk.CollectionConverters._
import scala.collection.mutable.Map
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.MessageReaction

object Anonbot extends ListenerAdapter:
  private val pendingQuestions = Map[String, (Long, String)]()

  override def onMessageReceived(event: MessageReceivedEvent): Unit =
    if event.isFromGuild then return

    val user = event.getAuthor
    if user.isBot then return

    val message = event.getMessage
    val content = message.getContentRaw

    handleDMQuestion(event.getJDA, user, message, content)

  override def onMessageReactionAdd(event: MessageReactionAddEvent): Unit =
    if !event.isFromGuild then
      val user = event.getUser
      if user != null && !user.isBot then
        handleConfirmationReaction(
          event.getJDA,
          user,
          event.getMessageIdLong,
          event.getReaction
        )

  override def onSlashCommandInteraction(
      event: SlashCommandInteractionEvent
  ): Unit =
    event.getName match
      case "help" => handleHelpCommand(event)
      case _      => sendUnknownCommandReply(event)

  private def handleDMQuestion(
      jda: JDA,
      user: User,
      message: Message,
      question: String
  ): Unit =
    if question.trim.isEmpty then
      user.openPrivateChannel().queue { channel =>
        channel.sendMessage("Please provide a question!").queue()
      }
    else
      val confirmationMessage = s"""
      |**Question to be asked anonymously:**
      |$question
      |
      |(To confirm, react to this message with 👍)
      """.stripMargin

      user.openPrivateChannel().queue { channel =>
        channel.sendMessage(confirmationMessage).queue { sentMessage =>
          pendingQuestions.put(user.getId, (sentMessage.getIdLong, question))
        }
      }

  private def handleConfirmationReaction(
      jda: JDA,
      user: User,
      messageId: Long,
      reaction: MessageReaction
  ): Unit =
    pendingQuestions.get(user.getId) match
      case Some((storedMessageId, question)) if storedMessageId == messageId =>
        if reaction.getEmoji.asUnicode.getAsCodepoints == "U+1f44d"
        then
          broadcastQuestion(jda, question)
          pendingQuestions.remove(user.getId)
          user.openPrivateChannel().queue { channel =>
            channel
              .sendMessage("Your question has been submitted anonymously!")
              .queue()
          }
      case _ => // Not a confirmation we're tracking
  private def handleHelpCommand(event: SlashCommandInteractionEvent): Unit =
    val helpMessage = """
      |**Anonymous Question Bot Help**
      |Simply send me a direct message with your question.
      |I'll ask you to confirm before sending it anonymously.
      |
      |Commands:
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

      guilds.foreach { guild =>
        guild.getDefaultChannel match
          case null =>
            println(s"Couldn't find a default channel in ${guild.getName}")
          case channel =>
            channel.asTextChannel.sendMessage(announcement).queue()
      }
