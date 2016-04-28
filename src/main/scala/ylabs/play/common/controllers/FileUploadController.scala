package ylabs.play.common.controllers

import java.io.ByteArrayInputStream
import java.util.Base64
import javax.inject.Inject

import play.api.libs.json.Json
import play.api.mvc.Controller
import springnz.util.Logging
import ylabs.play.common.models.FileUpload.{FileUploadRequest, FileUploadResponse, Url}
import ylabs.play.common.models.Helpers.ApiFailure.Failed
import ylabs.play.common.models.ValidationError.{Field, Invalid, Reason}
import ylabs.play.common.services.FileUploadService
import ylabs.play.common.utils.Authenticated

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FileUploadController @Inject() (fileUpload: FileUploadService)(implicit ec: ExecutionContext) extends Controller with Logging {
  def uploadFile() = Authenticated.async(parse.json[FileUploadRequest]) { request ⇒
    val data = request.body.data.value
    val desc = request.body.description
    val errorMessage = "Invalid Base64 data"
    Try {
      data.split(',') match {
        case Array(header, fileData) if header.startsWith("data:") && header.endsWith("base64") ⇒
          //base64 encoded will be something like 'data:image\/png;base64,iVBORw0KGgoAAAANSUhEUgAAAVI...' so remove beginning
          val decodedFile = new ByteArrayInputStream(Base64.getDecoder.decode(fileData))
          fileUpload.upload(decodedFile, desc)
        case _ ⇒ throw new Exception(errorMessage)
      }
    } match {
      case Success(future) ⇒ future map { url ⇒ Ok(Json.toJson(FileUploadResponse(Url(url.toString)))) } recover {
        case t: Throwable ⇒ log.error(s"Error uploading image to cloud", t); throw t
      }
      case Failure(t) ⇒
        val invalid = Invalid(Field("data"), Reason(errorMessage))
        val failure = Failed(List(invalid))
        Future.successful(BadRequest(Json.toJson(failure)))
    }
  }
}
