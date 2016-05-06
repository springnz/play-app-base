package ylabs.play.common.controllers

import java.io.{File, FileInputStream}
import java.nio.file.Paths
import javax.inject.Inject

import akka.util.Timeout
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import ylabs.play.common.dal.GraphDB
import ylabs.play.common.services.FileUploadService
import ylabs.play.common.utils.SmackUtils

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class HealthcheckController @Inject() (
    smackUtils: SmackUtils, graph: GraphDB, fileUpload: FileUploadService)(implicit ec: ExecutionContext) extends Controller {
  protected val log = LoggerFactory.getLogger(getClass.getSimpleName)
  implicit val timeout: Timeout = 5.seconds
  type ServiceIsDown = String

  def check() = Action.async { request ⇒
    val checks = Future.sequence(Seq(
      testEjabberd,
      testOrient,
      testS3))
    val errors = checks map { results ⇒
      results.collect {
        case Some(error) ⇒ error
      }
    }

    errors map {
      case Nil  ⇒ Ok("[]")
      case errs ⇒ ServiceUnavailable(Json.toJson(errs))
    }
  }

  private def testEjabberd: Future[Option[ServiceIsDown]] =
    Future {
      smackUtils.getChatRooms()
      None // no error
    }.recover { case t ⇒ log.error("ejabberd is down", t); Some("Ejabberd") }

  private def testOrient: Future[Option[ServiceIsDown]] =
    Future {
      graph.graph.V.count.headOption
      None // no error
    }.recover { case t ⇒ log.error("orientdb is down", t); Some("Orientdb") }

  private def testS3: Future[Option[ServiceIsDown]] =
    fileUpload.upload(
      file = new FileInputStream(new File(Paths.get("src/test/resources/healthcheck-upload.png").toAbsolutePath.toString)),
      description = None).map(_ ⇒ None /* no error */ )
      .recover { case t ⇒ log.error("S3 is down", t); Some("S3") }

}
