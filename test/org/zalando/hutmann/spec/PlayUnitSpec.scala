package org.zalando.hutmann.spec

import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import org.scalatestplus.play.{ PlaySpec, PortNumber, WsScalaTestClient }
import play.api.Application
import play.api.http.{ HeaderNames, MimeTypes }
import play.api.libs.ws.{ WSClient, WSResponse }

trait PlayUnitSpec extends PlaySpec with ScalaFutures with WsScalaTestClient {
  def myPublicAddress(): String
  implicit def portNumber: PortNumber

  def callWs(testPaymentGatewayURL: String)(implicit app: Application): WSResponse = {
    implicit val wSClient = app.injector.instanceOf[WSClient]
    val callbackURL = s"http://${myPublicAddress()}/callback"
    whenReady(
      wsUrl(testPaymentGatewayURL)
        .withQueryStringParameters("callbackURL" -> callbackURL)
        .withHttpHeaders(HeaderNames.ACCEPT -> MimeTypes.TEXT)
        .get(),
      Interval(Span(10, Seconds))
    ) { result =>
        result
      }
  }
}
