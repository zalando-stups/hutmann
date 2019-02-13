package org.zalando.hutmann.authentication

import java.io.{ ByteArrayOutputStream, PrintWriter }

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Framing, Keep, Sink }
import akka.util.ByteString
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import play.api.http.{ ContentTypeOf, Writeable }
import play.api.libs.Files.{ SingletonTemporaryFileCreator, TemporaryFile }
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import play.api.test.FakeRequest
import play.core.parsers.Multipart.{ FileInfo, _ }

import scala.concurrent.ExecutionContext.Implicits.global

class Helper(var mockDb: List[Int] = List()) {
  val temporaryFileCreator = SingletonTemporaryFileCreator
  val temporaryFile = temporaryFileCreator.create(prefix = "test_file")

  val printWriter = new PrintWriter(temporaryFile.path.toFile.getAbsolutePath)
  (1 to 10).foreach(i => printWriter.write(s"$i\n"))
  printWriter.close()

  val multipartFormData = MultipartFormData[TemporaryFile](
    dataParts = Map(),
    files = Seq(FilePart[TemporaryFile]("file", temporaryFile.path.toFile.getName, None, temporaryFile)),
    badParts = Nil
  )

  // A custom file part handler, which process the content of files in request body and store it into mockDb as well.
  def handleFilePart: FilePartHandler[Int] = {
    case FileInfo(partName, filename, contentType, _) =>
      val byteStreamToLineFlow: Flow[ByteString, String, NotUsed] = Flow[ByteString].
        via(Framing.delimiter(ByteString("\n"), 1000, allowTruncation = true)).map(_.utf8String)
      val sink = byteStreamToLineFlow.toMat(Sink.fold(List[Int]()){ (r, c) =>
        c.toInt +: r
      })(Keep.right).mapMaterializedValue { resFut =>
        resFut.map { res =>
          mockDb = res
          res.sum
        }
      }
      val accumulator = Accumulator(sink)
      accumulator.map(result => FilePart(partName, filename, contentType, result))
  }

  def multiPartFormDataWritable(request: FakeRequest[MultipartFormData[TemporaryFile]]): Writeable[MultipartFormData[TemporaryFile]] = {

    val builder = MultipartEntityBuilder.create()

    request.body.dataParts.foreach { case (k, vs) => builder.addTextBody(k, vs.mkString) }

    // ContentType part is necessary here because it gets parsed as a DataPart otherwise.
    request.body.files.foreach {
      case f =>
        builder.addBinaryBody("file", f.ref.path.toFile, ContentType.create(f.contentType.getOrElse("multipart/form-data"), "UTF-8"), f.filename)
    }

    val entity = builder.build()

    implicit val contentType: ContentTypeOf[MultipartFormData[TemporaryFile]] = {
      ContentTypeOf[MultipartFormData[TemporaryFile]](Some(entity.getContentType.getValue))
    }

    Writeable[MultipartFormData[TemporaryFile]] {
      (mfd: MultipartFormData[TemporaryFile]) =>
        val outputStream = new ByteArrayOutputStream()

        entity.writeTo(outputStream)

        ByteString(outputStream.toByteArray)
    }
  }
}
