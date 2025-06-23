package config

import scala.util.{Try, Success, Failure}
import utils.Logger

case class BotConfig(
    token: String,
    targetGuildId: String,
    targetChannelName: String = "fråga-anonymt",
    confirmationTimeoutMinutes: Int = 10,
    maxQuestionLength: Int = 10000
)

object BotConfig:
  def load(): Try[BotConfig] =
    for
      token <- getRequiredEnv("DISCORD_TOKEN")
      guildId <- getRequiredEnv("DISCORD_GUILD_ID")
    yield BotConfig(
      token = token,
      targetGuildId = guildId,
      targetChannelName =
        getOptionalEnv("DISCORD_CHANNEL_NAME").getOrElse("fråga-anonymt"),
      confirmationTimeoutMinutes = getOptionalEnv(
        "CONFIRMATION_TIMEOUT_MINUTES"
      ).flatMap(_.toIntOption).getOrElse(10),
      maxQuestionLength = getOptionalEnv("MAX_QUESTION_LENGTH")
        .flatMap(_.toIntOption)
        .getOrElse(10000)
    )

  private def getRequiredEnv(key: String): Try[String] =
    sys.env.get(key).filter(_.nonEmpty) match
      case Some(value) => Success(value)
      case None =>
        Failure(
          IllegalStateException(
            s"Missing or empty $key environment variable! See README.md for setup."
          )
        )

  private def getOptionalEnv(key: String): Option[String] =
    sys.env.get(key).filter(_.nonEmpty)
