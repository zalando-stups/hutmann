package org.zalando.hutmann.logging

import org.zalando.hutmann.authentication.User
import org.zalando.hutmann.spec.UnitSpec
import play.api.test.FakeRequest

import scala.util.Random

class LoggerDemo extends UnitSpec {
  val logger = Logger()

  def logStatements(implicit context: Context): Unit = {
    logger.trace("This is a test")
    logger.debug("This is a test")
    logger.info("This is a test")
    logger.warn("This is a test")
    logger.error("This is a test")
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
