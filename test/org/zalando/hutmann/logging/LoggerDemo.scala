package org.zalando.hutmann.logging

import org.zalando.hutmann.UnitSpec
import org.zalando.hutmann.authentication.User
import play.api.test.FakeRequest

import scala.util.Random

class LoggerDemo extends UnitSpec {
  def logStatements(implicit context: Context): Unit = {
    Logger.trace("This is a test")
    Logger.debug("This is a test")
    Logger.info("This is a test")
    Logger.warn("This is a test")
    Logger.error("This is a test")
  }

  "The logger" should "be demonstrated without a context" in {
    implicit val context: Context = NoContextAvailable
    logStatements
  }

  it should "be demonstrated with a request context" in {
    implicit val context: Context = RequestContext(Random.nextLong(), Some("abc123"), FakeRequest(), Right(User("abc", Map.empty, "realm", "Bearer", 100, None)))
    logStatements
  }

  it should "be demonstrated with a job context" in {
    implicit val context: Context = JobContext("FunnyRainbowJob", Some("abc123"))
    logStatements
  }
}
