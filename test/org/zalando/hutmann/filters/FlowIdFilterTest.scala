package org.zalando.hutmann.filters

import akka.stream.Materializer
import org.scalatestplus.play._
import org.zalando.hutmann.spec.{ PlayUnitSpec, UnitSpec }
import play.api.http.Status
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

class FlowIdFilterTest extends UnitSpec with OneAppPerSuite {
  implicit lazy val materializer: Materializer = app.materializer

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
    val filter = new CreateFlowIdFilter

    val result = filter.apply(nextFilter)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers("X-Flow-ID") should not be 'empty
    }
  }
  it should "overwrite flow ids that have been set in the service itself" in {
    val filter = new CreateFlowIdFilter

    val result = filter.apply(nextFilterWithFlowId)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK

      val headerFlowId = result.header.headers("X-Flow-ID")
      headerFlowId should not be 'empty
      headerFlowId should not be requestFlowId
    }
  }
  it should "use the given flow id when a flow id has been given" in {
    val filter = new CreateFlowIdFilter

    val result = filter.apply(nextFilter)(requestWithFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers("X-Flow-ID") shouldBe requestFlowId
    }
  }

  "A flow id filter with behaviour=Strict and copyToResult=true" should "not accept a request without a flow id, and the result should not have a flow id" in {
    val filter = new StrictFlowIdFilter

    val result = filter.apply(nextFilter)(requestWithoutFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.BAD_REQUEST
      result.header.headers.get("X-Flow-ID") shouldBe None
    }
  }
  it should "accept a request with a flow id and copy that to the result" in {
    val filter = new StrictFlowIdFilter

    val result = filter.apply(nextFilter)(requestWithFlowId).futureValue
    withClue(result){
      result.header.status shouldBe Status.OK
      result.header.headers("X-Flow-ID") shouldBe requestFlowId
    }
  }
}
