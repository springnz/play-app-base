package ylabs.play.common.models

import play.api.libs.json.Json
import ylabs.play.common.models.Helpers.IDJson

object ValidationError {
  case class Field(value: String) extends AnyVal
  case class Reason(value: String) extends AnyVal
  case class Invalid(field: Field, reason: Reason)
  implicit val fieldFormat = IDJson(Field)(Field.unapply)
  implicit val reasonFormat = IDJson(Reason)(Reason.unapply)
  implicit val invalidFormat = Json.format[Invalid]
}
