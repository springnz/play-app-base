package ylabs.play.common.models

import play.api.libs.json.Json
import ylabs.play.common.models.Helpers.{ApiRequest, ApiResponse, IDJson}

object FileUpload {
  case class FileDescription(value: String) extends AnyVal
  case class Base64String(value: String) extends AnyVal
  case class Url(value: String) extends AnyVal
  implicit val descriptionFormat = IDJson(FileDescription)(FileDescription.unapply)
  implicit val base64Format = IDJson(Base64String)(Base64String.unapply)
  implicit val urlFormat = IDJson(Url)(Url.unapply)

  case class FileUploadRequest(description: Option[FileDescription], data: Base64String) extends ApiRequest
  case class FileUploadResponse(url: Url) extends ApiResponse

  implicit val fileUploadRequestFormat = Json.format[FileUploadRequest]
  implicit val fileUploadResponseFormat = Json.format[FileUploadResponse]
}
