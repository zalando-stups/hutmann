package org.zalando.hutmann.logging

import org.scalatest.{ Assertion, AsyncFlatSpec, BeforeAndAfterEach, Matchers }
import org.zalando.hutmann.authentication.NoAuthorization
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

class ContextSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterEach {

  behavior of "Context"

  it should "propagate context in async execution" in {
    val context = RequestContext(Random.nextLong(), Some("abc123"), FakeRequest(), Left(NoAuthorization))
    Context.withContext(context) {
      val futureContext = Future {
        Context.getContext
      }
      futureContext.map { contextOpt =>
        contextOpt shouldBe context
      }
    }
  }

  it should "propagate context and restore old context" in {
    val oldContext = RequestContext(Random.nextLong(), Some("def456"), FakeRequest(), Left(NoAuthorization))
    Context.setContext(oldContext)

    val context = RequestContext(Random.nextLong(), Some("abc123"), FakeRequest(), Left(NoAuthorization))
    Context.withContext(context) {
      val currentContext = Context.getContext
      currentContext shouldBe context
    }

    val currentContext = Context.getContext
    currentContext shouldBe oldContext
  }

  it should "propagate context and restore empty context" in {
    val context = RequestContext(Random.nextLong(), Some("abc123"), FakeRequest(), Left(NoAuthorization))
    Context.withContext(context) {
      val currentContext = Context.getContext
      currentContext shouldBe context
    }

    val currentContext = Context.getContext
    currentContext shouldBe NoContextAvailable
  }

  it should "propagate context and restore old context in async execution" in {
    val oldContext = RequestContext(Random.nextLong(), Some("def456"), FakeRequest(), Left(NoAuthorization))
    val context = RequestContext(Random.nextLong(), Some("abc123"), FakeRequest(), Left(NoAuthorization))

    val futureContexts = Context.withContext(oldContext) {
      Future {
        val innerFuture = Context.withContext(context) {
          Future(Context.getContext)
        }
        val outerContext = Context.getContext
        innerFuture.map(_ -> outerContext)
      }.flatMap(identity)
    }

    val emptyContext = Context.getContext

    futureContexts.map {
      case (innerContext, outerContext) =>
        emptyContext shouldBe NoContextAvailable
        innerContext shouldBe context
        outerContext shouldBe oldContext
    }
  }

  implicit override val executionContext: ExecutionContext = {
    val app = new GuiceApplicationBuilder()
      .configure("akka.actor.default-dispatcher.type" -> "org.zalando.hutmann.dispatchers.ContextPropagatingDispatcherConfigurator")
      .build()

    app.injector.instanceOf[ExecutionContext]
  }

  def withExecutionContext(testCode: => Assertion): Future[Assertion] = {
    Future(testCode)(executionContext)
  }

  override def beforeEach(): Unit = {
    Context.setContext(NoContextAvailable)
  }

}

