package org.zalando.hutmann.logging

import java.time.ZonedDateTime

import org.slf4j.MDC
import org.zalando.hutmann.authentication.{ AuthorizationProblem, NoAuthorization, User, UserRequest }
import org.zalando.hutmann.filters.FlowIdFilter.FlowIdHeader
import play.api.mvc.{ Request, RequestHeader }

import scala.language.implicitConversions

/** Holds context information about what should be logged.*/
sealed trait Context {
  val contextInitializationTime = ZonedDateTime.now()
  val extraInfo: Map[String, String]
  def updated(key: String, value: String): Context
}

/**
  * The default value when no context information is available -
  * in this case, context information logging is omitted. Must be
  * given explicitly to enforce thinking about what you want to
  * achieve, instead of forgetting to log flow ids and only see it
  * on the live system when it is too late.
  */
case object NoContextAvailable extends Context {
  override val extraInfo: Map[String, String] = Map.empty
  def updated(key: String, value: String): NoContextAvailable.type = this
}

/** Marks objects that have a flow id. Comes with an extractor to get the flow id if needed.*/
trait FlowIdAware {
  val flowId: Option[String]
}

object FlowIdAware {
  /**
    * Unapply method for objects that have a flow id. Can be used to extract flow ids without a hassle, simply do it like this:
    * {{{
    *   //request is given
    *   val context: Context = request
    *   val optFlowId = context match {
    *     case FlowIdAware(flowId) => Some(flowId)
    *     case _ => None
    *   }
    * }}}
    */
  def unapply(flowIdAware: FlowIdAware): Option[String] = flowIdAware.flowId
}

/**
  * Context information that should be logged.
  *
  * @param requestId     The play request id that is unique for a request.
  * @param flowId        The flow id that is used to identify a flow over system boundaries.
  * @param requestHeader The request headers the request has. The request body is not forwarded here since you should do that explicitly if you need it.
  * @param user          The user that issued the request (if any).
  */
case class RequestContext(
    requestId:              Long,
    override val flowId:    Option[String],
    requestHeader:          RequestHeader,
    user:                   Either[AuthorizationProblem, User],
    override val extraInfo: Map[String, String]                = Map.empty
) extends Context with FlowIdAware {
  override def updated(key: String, value: String): RequestContext = this.copy(extraInfo = extraInfo.updated(key, value))
}

object RequestId {
  /**
    * Unapply method for objects that have a request id. Can be used to extract request ids without a hassle, simply do it like this:
    * {{{
    *   //request is given
    *   val context: Context = request
    *   val optFlowId = context match {
    *     case RequestId(requestId) => Some(requestId)
    *     case _ => None
    *   }
    * }}}
    */
  def unapply(requestContext: RequestContext): Option[Long] = Some(requestContext.requestId)
}

case class JobContext(
    name:                   String,
    override val flowId:    Option[String],
    override val extraInfo: Map[String, String] = Map.empty
) extends Context with FlowIdAware {
  override def updated(key: String, value: String): JobContext = this.copy(extraInfo = extraInfo.updated(key, value))
}

object Context {
  self =>
  private val context = new ThreadLocal[Context]() {
    override def initialValue(): Context = NoContextAvailable
  }

  def getContext: Context = {
    context.get()
  }

  def setContext(ctx: Context): Unit = {
    assert(ctx.isInstanceOf[Context], "context can't be null")
    context.set(ctx)
    ctx match {
      case FlowIdAware(flowId) =>
        MDC.put(FlowIdHeader, flowId)
      case NoContextAvailable =>
        MDC.remove(FlowIdHeader)
      case _ =>
    }
  }

  /**
    * Execute the given block with the given context.
    */
  def withContext[T](ctx: Context)(block: => T): T = {
    val oldContext = getContext
    try {
      setContext(ctx)
      block
    } finally {
      setContext(oldContext)
    }
  }

  /**
    * Implicit conversion to allow easy creation of {{{Context}}}. Usage:
    *
    * {{{
    * implicit val context: ContextInformation = request
    * }}}
    *
    * @param request The play request object that should be used to extract information.
    * @tparam A      The type of the body of the request.
    */
  implicit def request2loggingContext[A](request: UserRequest[A]): RequestContext = request.context

  /**
    * Implicit conversion to allow easy creation of {{{Context}}}. Usage:
    *
    * {{{
    * implicit val context: ContextInformation = request
    * }}}
    *
    * @param request The play request object that should be used to extract information.
    * @tparam A      The type of the body of the request.
    */
  implicit def request2loggingContext[A](request: Request[A]): RequestContext =
    RequestContext(requestId = request.id, flowId = request.headers.get(FlowIdHeader), requestHeader = request, user = Left(NoAuthorization))

  /**
    * Implicit conversion to allow easy creation of {{{Context}}}. Usage:
    *
    * {{{
    * implicit val context: ContextInformation = request
    * }}}
    *
    * @param request The play request object that should be used to extract information.
    * @tparam A      The type of the body of the request.
    */
  implicit def request2loggingContext[A](request: RequestHeader): RequestContext =
    RequestContext(requestId = request.id, flowId = request.headers.get(FlowIdHeader), requestHeader = request, user = Left(NoAuthorization))
}
