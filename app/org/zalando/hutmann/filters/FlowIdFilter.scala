package org.zalando.hutmann.filters

import scala.concurrent.Future
import scala.language.implicitConversions
import play.api.mvc.{ Result, RequestHeader, Filter }
import play.api.mvc.Results.BadRequest
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.util.{ Base64, UUID }

/**
  * A flow id filter that checks flow ids and can add them if they are not present, as well as copy them to the output
  * if needed.
  * @param behavior Accepts: <br />
  *                 <ul><li> Strict -> declines the request if the header doesn't contain a flow-id</li>
  *                 <li> Create -> creates a new flow-id if the header doesn't contain one</li></ul>
  * @param copyFlowIdToResult If the flow-id should be copied from the input to the output headers.
  */
final class FlowIdFilter(
    behavior:           FlowIdBehavior = Create,
    copyFlowIdToResult: Boolean        = true
) extends Filter {
  /**
    * Zero-Argument constructor, needed for Guice-Injection, where default-parameters are not enough.
    */
  def this() = this(Create, true)

  override def apply(nextFilter: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    val (filtered, optFlowId) = rh.headers.get("X-Flow-ID") match {
      case Some(id) =>
        (nextFilter(rh), Some(id))
      case None =>
        behavior match {
          case Create =>
            val flowId = createFlowId
            val newRh = rh.copy(headers = rh.headers.add("X-Flow-ID" -> flowId))
            (nextFilter(newRh), Some(flowId))
          case Strict => (Future.successful(BadRequest("Missing flow id header")), None)
        }
    }
    if (copyFlowIdToResult) {
      optFlowId.fold(filtered)(flowId => filtered.map(_.withHeaders("X-Flow-ID" -> flowId)))
    } else {
      filtered
    }
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

sealed trait FlowIdBehavior

case object Strict extends FlowIdBehavior

case object Create extends FlowIdBehavior
