package discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.entities.{Guild, Message, User}
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.MessageReaction

import scala.util.{Try, Success, Failure}
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

import java.util.concurrent.{
  ConcurrentHashMap,
  ScheduledExecutorService,
  Executors,
  TimeUnit
}
import java.time.Instant

import utils.Logger
import config.BotConfig
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel

case class PendingQuestion(
    messageId: Long,
    question: String,
    timestamp: Instant = Instant.now()
)

class Anonbot(config: BotConfig) extends ListenerAdapter:
  private val pendingQuestions =
    new ConcurrentHashMap[String, PendingQuestion]()
  private val cleanupExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor()
  private val THUMBS_UP_EMOJI = "U+1f44d"

  cleanupExecutor.scheduleAtFixedRate(
    () => cleanupExpiredQuestions(),
    1,
    1,
    TimeUnit.MINUTES
  )

  Logger.info(
    s"Anonbot instantiated for guild: ${config.targetGuildId}, channel: ${config.targetChannelName}"
  )

  // Städar upp resurser när boten stängs ner
  def cleanup(): Unit =
    Logger.info("Cleaning up Anonbot resources...")
    cleanupExecutor.shutdown()

  // Hanterar inkommande meddelanden - lyssnar bara på privata meddelanden
  override def onMessageReceived(event: MessageReceivedEvent): Unit =
    if event.isFromGuild then return

    val user = event.getAuthor
    if user.isBot then return

    val content = event.getMessage.getContentRaw.strip()
    handleDMQuestion(event.getJDA, user, content)

  // Hanterar när användare reagerar på meddelanden (för att bekräfta frågor)
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

  // Hanterar slash-kommandon som /manual
  override def onSlashCommandInteraction(
      event: SlashCommandInteractionEvent
  ): Unit =
    event.getName match
      case "manual" => handleHelpCommand(event)
      case _        => sendUnknownCommandReply(event)

  // Tar emot en fråga via DM och skickar bekräftelsemeddelande tillbaka
  private def handleDMQuestion(jda: JDA, user: User, question: String): Unit =
    if question.isEmpty then
      sendPrivateMessage(
        user,
        "Tomt meddelande detekterat. Inkludera din fråga i meddelandet, tack."
      )
    else if question.length > config.maxQuestionLength then
      Logger.warning(s"Question too long (${question.length} chars)")
      sendPrivateMessage(
        user,
        s"Din fråga är för lång (${question.length} tecken). Försök dra ner den under ${config.maxQuestionLength} tecken."
      )
    else
      Logger.info("Processing question from user")
      val confirmationMessage =
        s"""**Din anonyma fråga:**
           |$question
           |
           |Reagera med ${Emoji
            .fromUnicode(THUMBS_UP_EMOJI)
            .getFormatted()} för att bekräfta (går ut om ${config.confirmationTimeoutMinutes} minuter)""".stripMargin

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
                  Logger.info("Added pending question")

                  sentMessage
                    .addReaction(Emoji.fromUnicode(THUMBS_UP_EMOJI))
                    .queue(
                      _ => Logger.info("Added confirmation reaction"),
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

  // Kontrollerar om användaren bekräftat sin fråga med tummen upp-reaktion
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
        Logger.info("Question confirmed")
        sendQuestion(jda, pending.question)
        sendPrivateMessage(user, "Din fråga har skickats anonymt! ✅")
      }

  // Kollar om reaktionen är en tummen upp-emoji
  private def isThumbsUpReaction(reaction: MessageReaction): Boolean =
    Try {
      reaction.getEmoji.asUnicode.getAsCodepoints == THUMBS_UP_EMOJI
    }.getOrElse(false)

  // Visar hjälptext när användare skriver /manual
  private def handleHelpCommand(event: SlashCommandInteractionEvent): Unit =
    Logger.info("Help command requested")
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

  // Svarar när användare skriver ett okänt kommando
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

  // Skickar den anonyma frågan till målkanalen på Discord-servern
  private def sendQuestion(jda: JDA, question: String): Unit =
    findTargetChannel(jda) match
      case Some(channel) =>
        val announcement = s"**Inkommande anonym fråga:**\n$question"
        channel
          .sendMessage(announcement)
          .queue(
            _ =>
              Logger.success(
                s"Question sent to #${channel.getName} in ${channel.getGuild.getName}"
              ),
            error =>
              Logger.errorWithException("Failed to broadcast question", error)
          )
      case None =>
        Logger.error(
          s"Could not find target channel '#${config.targetChannelName}' in guild ${config.targetGuildId}"
        )

  // Hittar rätt textkanal att skicka frågan till
  private def findTargetChannel(jda: JDA): Option[TextChannel] =
    for
      guild <- Option(jda.getGuildById(config.targetGuildId))
      channel <- guild
        .getTextChannelsByName(config.targetChannelName, true)
        .asScala
        .headOption
    yield channel

  // Skickar ett privat meddelande till en användare
  private def sendPrivateMessage(user: User, message: String): Unit =
    user
      .openPrivateChannel()
      .queue(
        _.sendMessage(message).queue(
          _ => Logger.info("Private message sent"),
          error =>
            Logger.errorWithException("Failed to send private message", error)
        ),
        error =>
          Logger.errorWithException("Failed to open private channel", error)
      )

  // Tar bort gamla väntande frågor som inte bekräftats i tid
  private def cleanupExpiredQuestions(): Unit =
    val now = Instant.now()
    val cutoff = now.minusSeconds(config.confirmationTimeoutMinutes * 60)

    val expired = pendingQuestions.asScala
      .filter((_, pending) => pending.timestamp.isBefore(cutoff))
      .keys
      .toList

    expired.foreach { userId =>
      pendingQuestions.remove(userId)
      Logger.info("Removed expired question")
    }

    if expired.nonEmpty then
      Logger.info(s"Cleaned up ${expired.size} expired question(s)")
