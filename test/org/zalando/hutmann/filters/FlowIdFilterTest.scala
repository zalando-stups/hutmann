package org.zalando.hutmann.filters

import org.scalatestplus.play._
import org.zalando.hutmann.{ PlayUnitSpec, UnitSpec }
import org.zalando.hutmann.filters.{ Create, FlowIdFilter, Strict }
import play.api.http.Status
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

class FlowIdFilterTest extends PlayUnitSpec with OneServerPerSuite with FilterApp {
  implicit override lazy val app = myApp
  "FlowIdFilterTest" should {
    "validate header BAD_REQUEST" in {
      val testPaymentGatewayURL = s"http://$myPublicAddress/flowid"
      // The test payment gateway requires a callback to this server before it returns a result...
      // await is from play.api.test.FutureAwaits
      val response = callWs(testPaymentGatewayURL)

      response.status mustBe BAD_REQUEST
    }
  }
}

class FlowIdFilterTest2 extends PlayUnitSpec with OneServerPerSuite with AcceptHeaderApp {
  implicit override lazy val app = myApp
  "FlowIdFilterTest" should {
    "validate header OK" in {
      val testPaymentGatewayURL = s"http://$myPublicAddress/flowid"
      // The test payment gateway requires a callback to this server before it returns a result...
      // await is from play.api.test.FutureAwaits
      val response = callWs(testPaymentGatewayURL)

      response.status mustBe OK
    }
  }
}
class FlowIdFilterTest3 extends UnitSpec {
  val nextFilter: (RequestHeader) => Future[Result] = { rh: RequestHeader =>
    rh.headers("X-Flow-ID") should not be 'empty
    Future.successful(Results.Ok(""))
  }
  val nextFilterWithFlowId: (RequestHeader) => Future[Result] = { rh: RequestHeader =>
    rh.headers("X-Flow-ID") should not be 'empty
    Future.successful(Results.Ok("").withHeaders("X-Flow-ID" -> "unicorn"))
  }

  private val requestFlowId = "JLQIiMW+RweC/vM8nlcyUQ"
  val requestWithFlowId = FakeRequest().withHeaders("X-Flow-ID" -> requestFlowId)
  val requestWithoutFlowId = FakeRequest()

  FakeApplication

  "A flow id filter with behaviour=Create and copyToResult=true" should "create a flow id and copy it to the result when no flow id has been given" in {
    val filter = new FlowIdFilter(Create, copyFlowIdToResult = true)

    val result = filter.apply(nextFilter)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers("X-Flow-ID") should not be 'empty
    }
  }
  it should "overwrite flow ids that have been set in the service itself" in {
    val filter = new FlowIdFilter(Create, copyFlowIdToResult = true)

    val result = filter.apply(nextFilterWithFlowId)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK

      val headerFlowId = result.header.headers("X-Flow-ID")
      headerFlowId should not be 'empty
      headerFlowId should not be requestFlowId
    }
  }
  it should "use the given flow id when a flow id has been given" in {
    val filter = new FlowIdFilter(Create, copyFlowIdToResult = true)

    val result = filter.apply(nextFilter)(requestWithFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers("X-Flow-ID") shouldBe requestFlowId
    }
  }

  "A flow id filter with behaviour=Create and copyToResult=false" should "not copy flow ids to the result" in {
    val filter = new FlowIdFilter(Create, copyFlowIdToResult = false)

    val result = filter.apply(nextFilter)(requestWithFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers.get("X-Flow-ID") shouldBe None
    }
  }
  it should "leave flow ids the way they were when they are given in the service call itself" in {
    val filter = new FlowIdFilter(Create, copyFlowIdToResult = false)
    val nextFilter: (RequestHeader) => Future[Result] = { rh: RequestHeader =>
      rh.headers("X-Flow-ID") shouldBe requestFlowId
      Future.successful(Results.Ok(""))
    }

    val result = filter.apply(nextFilter)(requestWithFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers.get("X-Flow-ID") shouldBe None
    }
  }

  "A flow id filter with behaviour=Strict and copyToResult=true" should "not accept a request without a flow id, and the result should not have a flow id" in {
    val filter = new FlowIdFilter(Strict, copyFlowIdToResult = true)

    val result = filter.apply(nextFilter)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.BAD_REQUEST
      result.header.headers.get("X-Flow-ID") shouldBe None
    }
  }
  it should "accept a request with a flow id and copy that to the result" in {
    val filter = new FlowIdFilter(Strict, copyFlowIdToResult = true)

    val result = filter.apply(nextFilter)(requestWithFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers("X-Flow-ID") shouldBe requestFlowId
    }
  }
}

trait AcceptHeaderApp extends TestApp {
  def port: Int
  val myPublicAddress = s"localhost:$port"
  object TestFilter extends WithFilters(new FlowIdFilter(Create))
  lazy val myApp: FakeApplication = myApp(TestFilter)
}

trait FilterApp extends TestApp {
  def port: Int
  val myPublicAddress = s"localhost:$port"
  object TestFilter extends WithFilters(new FlowIdFilter(Strict))
  lazy val myApp: FakeApplication = myApp(TestFilter)
}

trait TestApp {
  def myApp(TestFilter: WithFilters): FakeApplication =
    FakeApplication(
      additionalConfiguration = Map("ehcacheplugin" -> "disabled"),
      withRoutes = {
        case ("GET", "/flowid") => Action { request =>
          request.headers("X-Flow-ID") match {
            case _: String => Results.Ok
            case _         => Results.BadRequest
          }
        }
      },
      withGlobal = Some(TestFilter)
    )
}
