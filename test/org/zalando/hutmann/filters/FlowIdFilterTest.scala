package org.zalando.hutmann.filters

import javax.inject.Inject

import akka.stream.Materializer
import org.scalatestplus.play._
import org.zalando.hutmann.spec.{ PlayUnitSpec, UnitSpec }
import play.api.Application
import play.api.http.{ DefaultHttpFilters, HttpFilters, HttpVerbs, Status }
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import FlowIdFilter.FlowIdHeader

import scala.concurrent.Future

class FlowIdFilterTest extends PlayUnitSpec with OneServerPerSuite with TestApp {
  implicit override lazy val app = createApp(classOf[StrictFilters])
  "FlowIdFilterTest" should {
    "validate header BAD_REQUEST" in {
      val testPaymentGatewayURL = "/flowid"
      // The test payment gateway requires a callback to this server before it returns a result...
      // await is from play.api.test.FutureAwaits
      val response = callWs(testPaymentGatewayURL)

      response.status mustBe BAD_REQUEST
    }
  }
}

class FlowIdFilterTest2 extends PlayUnitSpec with OneServerPerSuite with TestApp {
  implicit override lazy val app = createApp(classOf[CreateFilters])
  "FlowIdFilterTest" should {
    "validate header OK" in {
      val testPaymentGatewayURL = "/flowid"
      // The test payment gateway requires a callback to this server before it returns a result...
      // await is from play.api.test.FutureAwaits
      val response = callWs(testPaymentGatewayURL)

      response.status mustBe OK
    }
  }
}

class FlowIdFilterTest3 extends UnitSpec with OneAppPerSuite {
  implicit lazy val materializer: Materializer = app.materializer

  val nextFilter: (RequestHeader) => Future[Result] = { rh: RequestHeader =>
    rh.headers(FlowIdHeader) should not be 'empty
    Future.successful(Results.Ok(""))
  }
  val nextFilterWithFlowId: (RequestHeader) => Future[Result] = { rh: RequestHeader =>
    rh.headers(FlowIdHeader) should not be 'empty
    Future.successful(Results.Ok("").withHeaders(FlowIdHeader -> "unicorn"))
  }

  private val requestFlowId = "JLQIiMW+RweC/vM8nlcyUQ"
  val requestWithFlowId = FakeRequest().withHeaders(FlowIdHeader -> requestFlowId)
  val requestWithoutFlowId = FakeRequest()

  FakeApplication

  "A flow id filter with behaviour=Create and copyToResult=true" should "create a flow id and copy it to the result when no flow id has been given" in {
    val filter = new CreateFlowIdFilter

    val result = filter.apply(nextFilter)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers(FlowIdHeader) should not be 'empty
    }
  }
  it should "overwrite flow ids that have been set in the service itself" in {
    val filter = new CreateFlowIdFilter

    val result = filter.apply(nextFilterWithFlowId)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK

      val headerFlowId = result.header.headers(FlowIdHeader)
      headerFlowId should not be 'empty
      headerFlowId should not be requestFlowId
    }
  }
  it should "use the given flow id when a flow id has been given" in {
    val filter = new CreateFlowIdFilter

    val result = filter.apply(nextFilter)(requestWithFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers(FlowIdHeader) shouldBe requestFlowId
    }
  }

  "A flow id filter with behaviour=Strict and copyToResult=true" should "not accept a request without a flow id, and the result should not have a flow id" in {
    val filter = new StrictFlowIdFilter

    val result = filter.apply(nextFilter)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.BAD_REQUEST
      result.header.headers.get(FlowIdHeader) shouldBe None
    }
  }
  it should "accept a request with a flow id and copy that to the result" in {
    val filter = new StrictFlowIdFilter

    val result = filter.apply(nextFilter)(requestWithFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers(FlowIdHeader) shouldBe requestFlowId
    }
  }
}

class CreateFilters @Inject() (flowIdFilter: CreateFlowIdFilter) extends DefaultHttpFilters(flowIdFilter)
class StrictFilters @Inject() (flowIdFilter: StrictFlowIdFilter) extends DefaultHttpFilters(flowIdFilter)

trait TestApp {
  def port: Int
  val myPublicAddress = s"localhost:$port"

  def createApp(filter: Class[_ <: HttpFilters]): Application =
    new GuiceApplicationBuilder()
      .configure(Map("ehcacheplugin" -> "disabled"))
      .routes(
        {
          case (HttpVerbs.GET, "/flowid") => Action { request =>
            request.headers(FlowIdHeader) match {
              case _: String => Results.Ok
              case _         => Results.BadRequest
            }
          }
        }
      )
      .overrides(bind[HttpFilters].to(filter))
      .build
}
