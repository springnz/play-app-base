package ylabs.play.common.models

import play.api.libs.json.Json
import ylabs.play.common.models.Helpers.IDJson
import ylabs.play.common.models.User.Phone

object Sms {
  case class Sms(from: From, to: Phone, text: Text) {
    def asMap: Map[String, String] =
      Map("From" → from.value, "To" → to.value, "Body" → text.value)
  }

  case class SmsStatusChanged(From: From, To: Phone, Body: Text, MessageStatus: Status, ErrorCode: ErrorCode)

  case class Smid(value: String) extends AnyVal

  case class StatusCallback(value: String) extends AnyVal
  case class From(value: String) extends AnyVal
  case class Text(value: String) extends AnyVal

  case class ErrorCode(value: String) extends AnyVal
  case class Status(value: String) extends AnyVal

  implicit val smidFormat = IDJson(Smid.apply)(Smid.unapply)
  implicit val fromFormat = IDJson(From.apply)(From.unapply)
  implicit val textFormat = IDJson(Text.apply)(Text.unapply)
  implicit val statusFormat = IDJson(Status.apply)(Status.unapply)
  implicit val errorFormat = IDJson(ErrorCode.apply)(ErrorCode.unapply)
  implicit val smsStatusFormat = Json.format[SmsStatusChanged]
}