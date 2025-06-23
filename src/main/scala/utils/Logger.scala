package utils

enum LogLevel:
  case INFO, WARNING, ERROR, SUCCESS

object Logger:
  private var currentLevel: LogLevel = LogLevel.INFO

  def setLevel(level: LogLevel): Unit = currentLevel = level

  private def shouldLog(level: LogLevel): Boolean =
    level.ordinal >= currentLevel.ordinal

  private def log(level: LogLevel, message: String): Unit =
    if shouldLog(level) then
      val timestamp = java.time.LocalTime.now().toString.take(8)
      val levelStr = level.toString
      println(s"[$timestamp] [$levelStr] $message")

  def info(message: String): Unit = log(LogLevel.INFO, message)
  def warning(message: String): Unit = log(LogLevel.WARNING, message)
  def error(message: String): Unit = log(LogLevel.ERROR, message)
  def errorWithException(message: String, error: Throwable): Unit =
    log(LogLevel.ERROR, message + error.getMessage())
  def success(message: String): Unit = log(LogLevel.SUCCESS, message)
