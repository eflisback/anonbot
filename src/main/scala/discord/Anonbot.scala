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

import utils.Logger

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
      sendPrivateMessage(
        user,
        "Tomt meddelande detekterat. Inkludera din fråga i meddelandet, tack."
      )
    else if question.length > MAX_QUESTION_LENGTH then
      Logger.warning(
        s"Question too long (${question.length} chars)"
      )
      sendPrivateMessage(
        user,
        s"Din fråga är för lång (${question.length} tecken). Försök dra ner den under $MAX_QUESTION_LENGTH tecken."
      )
    else
      Logger.info(s"Processing question from user")
      val confirmationMessage =
        s"""**Din anonyma fråga:**
           |$question
           |
           |Reagera med ${Emoji
            .fromUnicode(
              THUMBS_UP_EMOJI
            )
            .getFormatted()} för att bekräfta (går ut om $CONFIRMATION_TIMEOUT_MINUTES minuter)""".stripMargin

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
                  Logger
                    .info(s"Added pending question")

                  sentMessage
                    .addReaction(Emoji.fromUnicode(THUMBS_UP_EMOJI))
                    .queue(
                      _ =>
                        Logger.info(
                          s"Added confirmation reaction"
                        ),
                      error =>
                        Logger
                          .errorWithException("Failed to add reaction", error)
                    )
                },
                error =>
                  Logger.errorWithException(
                    "Failed to send confirmation message",
                    error
                  )
              )
          },
          error =>
            Logger.errorWithException("Failed to open private channel", error)
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
        Logger.info(s"Question confirmed")
        broadcastQuestion(jda, pending.question)
        sendPrivateMessage(
          user,
          "Din fråga har skickats anonymt! ✅"
        )
      }

  private def isThumbsUpReaction(reaction: MessageReaction): Boolean =
    Try {
      reaction.getEmoji.asUnicode.getAsCodepoints == THUMBS_UP_EMOJI
    }.getOrElse(false)

  private def handleHelpCommand(event: SlashCommandInteractionEvent): Unit =
    Logger.info(s"Help command requested")
    val helpMessage =
      """**Anonbot, manual**
        |
        |📝 **Hur fungerar jag?**
        |1. Skicka en fråga du önskar ska ställas anonymt till mig privat
        |2. Jag svarar med en förhandsgranskning och ber dig bekräfta
        |3. Reagera med 👍 för att ställa frågan anonymt
        |
        |⚡ **Commands:**
        |`/manual` - Visa detta meddelande""".stripMargin

    event
      .reply(helpMessage)
      .setEphemeral(true)
      .queue(
        _ => Logger.info("Help message sent successfully"),
        error => Logger.errorWithException("Failed to send help message", error)
      )

  private def sendUnknownCommandReply(
      event: SlashCommandInteractionEvent
  ): Unit =
    event
      .reply(
        "❓ Jag känner inte igen kommandot. Använd `/manual` för att visa användarmanualen."
      )
      .setEphemeral(true)
      .queue(
        _ => {},
        error =>
          Logger
            .errorWithException("Failed to send unknown command reply", error)
      )

  private def broadcastQuestion(jda: JDA, question: String): Unit =
    val guilds = jda.getGuilds.asScala.toList

    if guilds.isEmpty then Logger.warning("Bot is not in any guilds!")
    else
      Logger.info(s"Broadcasting question to ${guilds.size} guild(s)")
      val announcement = s"**Inkommande anonym fråga:**\n\n$question"

      guilds.foreach { guild =>
        Option(guild.getDefaultChannel) match
          case None =>
            Logger.warning(
              s"No default channel found in guild: ${guild.getName}"
            )
          case Some(channel) =>
            channel.asTextChannel
              .sendMessage(announcement)
              .queue(
                _ => Logger.success(s"Question broadcast to ${guild.getName}"),
                error =>
                  Logger.errorWithException(
                    s"Failed to broadcast to ${guild.getName}",
                    error
                  )
              )
      }

  private def sendPrivateMessage(user: User, message: String): Unit =
    user
      .openPrivateChannel()
      .queue(
        _.sendMessage(message).queue(
          _ => Logger.info(s"Private message sent"),
          error =>
            Logger.errorWithException("Failed to send private message", error)
        ),
        error =>
          Logger.errorWithException("Failed to open private channel", error)
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
      Logger.info(s"Removed expired question")
    }

    if expired.nonEmpty then
      Logger.info(s"Cleaned up ${expired.size} expired question(s)")
