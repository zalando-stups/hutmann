package org.zalando.hutmann.logging

import java.time.{ Duration, ZonedDateTime }

trait Logger {
  protected def createLogString(message: => String, context: Context, file: sourcecode.File, line: sourcecode.Line): String = {
    val codeContext = s"${file.value.substring(file.value.lastIndexOf("/") + 1)}:${line.value}"
    val flowDuration = Duration.between(context.contextInitializationTime, ZonedDateTime.now())

    val contextInfo = context match {
      case RequestContext(requestId, Some(flowId), _, _) => s"${flowDuration.toMillis}ms/$codeContext/$flowId"
      case RequestContext(requestId, None, _, _)         => s"${flowDuration.toMillis}ms/$codeContext/requestId_$requestId"
      case JobContext(name, Some(flowId), _)             => s"${flowDuration.toMillis}ms/$codeContext/$flowId - $name"
      case JobContext(name, None, _)                     => s"${flowDuration.toMillis}ms/$codeContext/$name"
      case NoContextAvailable                            => s"$codeContext/NoContextAvailable"
    }

    s"$message - $contextInfo"
  }

  def isTraceEnabled: Boolean = play.api.Logger.isTraceEnabled
  def isDebugEnabled: Boolean = play.api.Logger.isDebugEnabled
  def isInfoEnabled: Boolean = play.api.Logger.isInfoEnabled
  def isWarnEnabled: Boolean = play.api.Logger.isWarnEnabled
  def isErrorEnabled: Boolean = play.api.Logger.isErrorEnabled

  def trace(message: => String)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.trace(createLogString(message, context, file, line))
  def trace(message: => String, error: => Throwable)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.trace(createLogString(message, context, file, line), error)

  def debug(message: => String)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.debug(createLogString(message, context, file, line))
  def debug(message: => String, error: => Throwable)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.debug(createLogString(message, context, file, line), error)

  def info(message: => String)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.info(createLogString(message, context, file, line))
  def info(message: => String, error: => Throwable)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.info(createLogString(message, context, file, line), error)

  def warn(message: => String)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.warn(createLogString(message, context, file, line))
  def warn(message: => String, error: => Throwable)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.warn(createLogString(message, context, file, line), error)

  def error(message: => String)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.error(createLogString(message, context, file, line))
  def error(message: => String, error: => Throwable)(implicit context: Context, file: sourcecode.File, line: sourcecode.Line): Unit =
    play.api.Logger.error(createLogString(message, context, file, line), error)
}

/**
  * Replaces the default play logger. Has access functionality that is
  * equal to the play logger, and uses that logger underneath. However, it
  * additionally puts context information in the log output, like flow ids.
  * It does not implement {{{LoggerLike}}} though, since we change the
  * function declarations.
  *
  * Giving an implicit context is needed - you can't simply omit the
  * implicit parameter. This is for a reason: It lets the compiler check if
  * you have enough information to write a logging statement with flow ids,
  * or not - instead of seeing this on the live system when it is too late.
  * If you really do not want to have a context, you can supply the case object
  * {{{NoContextAvailable}}} - either explicitly, or as an implicit value.
  */
object Logger extends Logger
