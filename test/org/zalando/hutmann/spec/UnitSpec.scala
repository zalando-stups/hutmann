package org.zalando.hutmann.spec

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.{ FlatSpec, Matchers }

trait UnitSpec extends FlatSpec with Matchers with ScalaFutures {
  implicit val defaultPatience = PatienceConfig(timeout = Span(8, Seconds), interval = Span(50, Millis))
}
