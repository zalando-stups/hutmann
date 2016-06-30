package org.zalando.hutmann.spec

import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.libs.ws.{ WS, WSResponse }

trait PlayUnitSpec extends PlaySpec with ScalaFutures {
  def myPublicAddress(): String

  def callWs(testPaymentGatewayURL: String)(implicit app: Application): WSResponse = {
    val callbackURL = s"http://${myPublicAddress()}/callback"

    whenReady(
      WS.url(testPaymentGatewayURL)
        .withQueryString("callbackURL" -> callbackURL)
        .withHeaders("Accept" -> "text/plain")
        .get(),
      Interval(Span(10, Seconds))
    ) { result =>
        result
      }
  }
}
