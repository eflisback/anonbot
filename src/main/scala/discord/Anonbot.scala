package discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.entities.{Guild, Message, User}
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.MessageReaction

import scala.jdk.CollectionConverters.*
import java.util.concurrent.{
  ConcurrentHashMap,
  ScheduledExecutorService,
  Executors,
  TimeUnit
}
import java.time.Instant
import scala.util.{Try, Success, Failure}

case class PendingQuestion(
    messageId: Long,
    question: String,
    timestamp: Instant = Instant.now()
)

object Anonbot extends ListenerAdapter:
  private val pendingQuestions =
    new ConcurrentHashMap[String, PendingQuestion]()
  private val cleanupExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor()
  private val CONFIRMATION_TIMEOUT_MINUTES = 10
  private val THUMBS_UP_EMOJI = "U+1f44d"

  cleanupExecutor.scheduleAtFixedRate(
    () => cleanupExpiredQuestions(),
    1,
    1,
    TimeUnit.MINUTES
  )

  def cleanup(): Unit =
    cleanupExecutor.shutdown()

  override def onMessageReceived(event: MessageReceivedEvent): Unit =
    if event.isFromGuild then return

    val user = event.getAuthor
    if user.isBot then return

    val message = event.getMessage
    val content = message.getContentRaw.strip()

    handleDMQuestion(event.getJDA, user, content)

  override def onMessageReactionAdd(event: MessageReactionAddEvent): Unit =
    if event.isFromGuild then return

    Option(event.getUser).filterNot(_.isBot).foreach { user =>
      handleConfirmationReaction(
        event.getJDA,
        user,
        event.getMessageIdLong,
        event.getReaction
      )
    }

  override def onSlashCommandInteraction(
      event: SlashCommandInteractionEvent
  ): Unit =
    event.getName match
      case "help" => handleHelpCommand(event)
      case _      => sendUnknownCommandReply(event)

  private def handleDMQuestion(jda: JDA, user: User, question: String): Unit =
    val MAX_QUESTION_LENGTH = 10000

    if question.isEmpty then
      sendPrivateMessage(user, "Please provide a question!")
    else if question.length > MAX_QUESTION_LENGTH then
      sendPrivateMessage(
        user,
        s"Your question is too long. Please keep it under $MAX_QUESTION_LENGTH characters."
      )
    else
      val confirmationMessage =
        s"""**Question to be asked anonymously:**
           |$question
           |
           |React with ${Emoji
            .fromUnicode(
              THUMBS_UP_EMOJI
            )
            .getFormatted()} to confirm (expires in $CONFIRMATION_TIMEOUT_MINUTES minutes)""".stripMargin

      user
        .openPrivateChannel()
        .queue(
          channel => {
            channel
              .sendMessage(confirmationMessage)
              .queue(
                sentMessage => {
                  val pending = PendingQuestion(sentMessage.getIdLong, question)
                  pendingQuestions.put(user.getId, pending)

                  sentMessage
                    .addReaction(Emoji.fromUnicode(THUMBS_UP_EMOJI))
                    .queue(
                      _ => {},
                      error =>
                        println(
                          s"[Error] Failed to add reaction: ${error.getMessage}"
                        )
                    )
                },
                error =>
                  println(
                    s"[Error] Failed to send confirmation message: ${error.getMessage}"
                  )
              )
          },
          error =>
            println(
              s"[Error] Failed to open private channel: ${error.getMessage}"
            )
        )

  private def handleConfirmationReaction(
      jda: JDA,
      user: User,
      messageId: Long,
      reaction: MessageReaction
  ): Unit =
    Option(pendingQuestions.get(user.getId))
      .filter(_.messageId == messageId)
      .filter(_ => isThumbsUpReaction(reaction))
      .foreach { pending =>
        pendingQuestions.remove(user.getId)
        broadcastQuestion(jda, pending.question)
        sendPrivateMessage(
          user,
          "Your question has been submitted anonymously! ✅"
        )
      }

  private def isThumbsUpReaction(reaction: MessageReaction): Boolean =
    Try {
      reaction.getEmoji.asUnicode.getAsCodepoints == THUMBS_UP_EMOJI
    }.getOrElse(false)

  private def handleHelpCommand(event: SlashCommandInteractionEvent): Unit =
    val helpMessage =
      """**Anonymous Question Bot Help**
        |
        |📝 **How to use:**
        |1. Send me a direct message with your question
        |2. I'll show you a preview and ask for confirmation
        |3. React with 👍 to submit anonymously
        |
        |⚡ **Commands:**
        |`/help` - Show this help message""".stripMargin

    event
      .reply(helpMessage)
      .setEphemeral(true)
      .queue(
        _ => {},
        error =>
          println(s"[Error] Failed to send help message: ${error.getMessage}")
      )

  private def sendUnknownCommandReply(
      event: SlashCommandInteractionEvent
  ): Unit =
    event
      .reply("❓ Unknown command. Use `/help` for available options.")
      .setEphemeral(true)
      .queue(
        _ => {},
        error =>
          println(
            s"[Error] Failed to send unknown command reply: ${error.getMessage}"
          )
      )

  private def broadcastQuestion(jda: JDA, question: String): Unit =
    val guilds = jda.getGuilds.asScala.toList

    if guilds.isEmpty then println("[Warning] Bot is not in any guilds!")
    else
      val announcement = s"**❓ Inkommande anonym fråga:**\n$question"

      guilds.foreach { guild =>
        Option(guild.getDefaultChannel) match
          case None =>
            println(
              s"[Warning] Couldn't find a default channel in ${guild.getName}"
            )
          case Some(channel) =>
            channel.asTextChannel
              .sendMessage(announcement)
              .queue(
                _ =>
                  println(s"[Success] Question broadcast to ${guild.getName}"),
                error =>
                  println(
                    s"[Error] Failed to broadcast to ${guild.getName}: ${error.getMessage}"
                  )
              )
      }

  private def sendPrivateMessage(user: User, message: String): Unit =
    user
      .openPrivateChannel()
      .queue(
        _.sendMessage(message).queue(
          _ => {},
          error =>
            println(s"Failed to send private message: ${error.getMessage}")
        ),
        error => println(s"Failed to open private channel: ${error.getMessage}")
      )

  private def cleanupExpiredQuestions(): Unit =
    val now = Instant.now()
    val cutoff = now.minusSeconds(CONFIRMATION_TIMEOUT_MINUTES * 60)

    val expired = pendingQuestions.asScala
      .filter((_, pending) => pending.timestamp.isBefore(cutoff))
      .keys
      .toList

    expired.foreach { userId =>
      pendingQuestions.remove(userId)
    }

    if expired.nonEmpty then
      println(s"[Info] Cleaned up ${expired.size} expired question(s)")
