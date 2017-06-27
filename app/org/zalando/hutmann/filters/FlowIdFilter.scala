package org.zalando.hutmann.filters

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions
import play.api.mvc.{ Filter, RequestHeader, Result }
import play.api.mvc.Results.BadRequest
import java.util.{ Base64, UUID }

import akka.stream.Materializer
import com.google.inject.Inject
import FlowIdFilter.FlowIdHeader
import org.zalando.hutmann.logging.Context

sealed abstract class FlowIdFilter(implicit val mat: Materializer, implicit val ec: ExecutionContext) extends Filter {
  self: FlowIdBehavior with TraceBehavior =>

  override def apply(nextFilter: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    val optFlowId = getFlowId(rh)
    optFlowId match {
      case None =>
        Future.successful(BadRequest("Missing flow id header"))
      case Some(flowId) =>
        val headers = rh.withHeaders(rh.headers.add(FlowIdHeader -> flowId))
        trace(headers) {
          nextFilter(headers).map(_.withHeaders(FlowIdHeader -> flowId))
        }
    }
  }

}

sealed trait TraceBehavior {
  def trace[T](requestHeader: RequestHeader)(block: => T): T
}

sealed trait MdcTraceBehavior extends TraceBehavior {
  def trace[T](requestHeader: RequestHeader)(block: => T): T = {
    val ctx = Context.request2loggingContext(requestHeader)
    Context.withContext(ctx)(block)
  }
}

sealed trait NoTraceBehavior extends TraceBehavior {
  def trace[T](requestHeader: RequestHeader)(block: => T): T = block
}

sealed trait FlowIdBehavior {
  def getFlowId(requestHeader: RequestHeader): Option[String]
}

sealed trait StrictFlowIdBehavior extends FlowIdBehavior {
  def getFlowId(requestHeader: RequestHeader): Option[String] = {
    requestHeader.headers.get(FlowIdHeader)
  }
}

sealed trait CreateFlowIdBehavior extends FlowIdBehavior {

  def getFlowId(requestHeader: RequestHeader): Option[String] = {
    requestHeader.headers.get(FlowIdHeader).orElse(Some(createFlowId))
  }

  private def createFlowId: String = {
    val uuid = UUID.randomUUID()
    val uuidByte = uuid.toByte
    val base64 = Base64.getEncoder.encodeToString(uuidByte).replace("=", "").drop(1)
    s"J$base64"
  }

  private implicit class UUIDExtension(self: UUID) {
    def toByte: Array[Byte] = {
      val msb = self.getMostSignificantBits
      val lsb = self.getLeastSignificantBits

      Array(
        (msb >>> 56).toByte,
        (msb >>> 48).toByte,
        (msb >>> 40).toByte,
        (msb >>> 32).toByte,
        (msb >>> 24).toByte,
        (msb >>> 16).toByte,
        (msb >>> 8).toByte,
        msb.toByte,
        (lsb >>> 56).toByte,
        (lsb >>> 48).toByte,
        (lsb >>> 40).toByte,
        (lsb >>> 32).toByte,
        (lsb >>> 24).toByte,
        (lsb >>> 16).toByte,
        (lsb >>> 8).toByte,
        lsb.toByte
      )
    }
  }
}

final class CreateFlowIdFilter @Inject() (implicit
  mat: Materializer,
                                          override implicit val ec: ExecutionContext) extends FlowIdFilter with CreateFlowIdBehavior with NoTraceBehavior
final class StrictFlowIdFilter @Inject() (implicit
  mat: Materializer,
                                          override implicit val ec: ExecutionContext) extends FlowIdFilter with StrictFlowIdBehavior with NoTraceBehavior

final class MdcCreateFlowIdFilter @Inject() (implicit
  mat: Materializer,
                                             override implicit val ec: ExecutionContext) extends FlowIdFilter with CreateFlowIdBehavior with MdcTraceBehavior
final class MdcStrictFlowIdFilter @Inject() (implicit
  mat: Materializer,
                                             override implicit val ec: ExecutionContext) extends FlowIdFilter with StrictFlowIdBehavior with MdcTraceBehavior

object FlowIdFilter {
  val FlowIdHeader: String = "X-Flow-ID"
}
