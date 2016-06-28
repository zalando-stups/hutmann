package org.zalando.hutmann.filters

import org.zalando.hutmann.logging.{ Context, Logger }
import play.api.http.HeaderNames
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Adds a logging filter to each request, which will log the method, url and resulting status code. Optionally, you
  * can log Headers. It will however not log authentication information.
  *
  * If you use the FlowIdFilter, please ensure you inject the FlowIdFilter first, so flow id's can be logged too.
  */
final class LoggingFilter(logHeaders: Boolean)(implicit ec: ExecutionContext) extends Filter {
  def this()(implicit ec: ExecutionContext) = this(logHeaders = false)

  val accessToken = "access_token"
  val logger = Logger("org.zalando.hutmann.filters")

  /**
    * Function that deletes some values from the given sequence of key/value pairs, depending on the key.
    * @param keyToFilter      The key that should not get logged
    * @param replacementValue The replacement that should be used instead of the original value
    * @param separator        The separator that will be used when creating the concatenated string
    *
    * @return A string that does not contain "keyToFilter", but all others.
    */
  def deletingStringMapper(keyToFilter: String, replacementValue: String = "DELETED", separator: String = "="): ((String, String)) => String = { x =>
    val (key, value) = x
    if (key.equalsIgnoreCase(keyToFilter)) {
      val tokenLength = value.length
      s"$key$separator$replacementValue(orig.length=$tokenLength)"
    } else {
      s"$key$separator$value"
    }
  }

  override def apply(nextFilter: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis
    implicit val context: Context = rh
    nextFilter(rh).map { result =>
      val eTag = result.header.headers.get("etag") match {
        case Some(eTag) => s" (eTag $eTag)"
        case None       => " (eTag unset)"
      }
      val requestTime = System.currentTimeMillis - startTime

      //create query string as it is transferred like, but without "access_token" fields.
      val queryString = rh.queryString.map(x => (x._1, x._2.mkString(","))).map(deletingStringMapper(accessToken)).mkString("&") match {
        case ""    => ""
        case other => s"?$other"
      }

      //this is the line that will get printed anyway, no matter what
      val headLine = result.header.headers.get("x-flow-id") match {
        case Some(flowId) =>
          s"$flowId - ${rh.method} ${rh.path}$queryString returned ${result.header.status}$eTag, took ${requestTime}ms"
        case None =>
          s"${rh.method} ${rh.path}$queryString returned ${result.header.status}$eTag, took ${requestTime}ms"
      }

      //set how headers should be logged (e.g. remove Authorization headers)
      lazy val filteredInputHeaders = rh.headers.headers.map(deletingStringMapper(HeaderNames.AUTHORIZATION, separator = ": "))
      lazy val inputHeaderLine = filteredInputHeaders.mkString(", ")
      lazy val outputHeaderLine = result.header.headers.map(header => s"${header._1}: ${header._2}").mkString(", ")

      //now decide what exactly will be logged
      val logLine = if (logHeaders) {
        s"$headLine\n\tInput Headers: $inputHeaderLine\n\tOutput Headers: $outputHeaderLine"
      } else {
        headLine
      }
      logger.info(logLine)

      result
    }
  }
}
