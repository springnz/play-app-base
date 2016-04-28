package ylabs.play.common.models

import java.util.UUID

import play.api.libs.json._

object Helpers {
  case class Id[+T](value: String = UUID.randomUUID().toString) extends AnyVal

  implicit def idConverter[T] = new Format[Id[T]] {
    override def reads(json: JsValue): JsResult[Id[T]] = {
      json.validate[String].map(Id.apply)
    }

    override def writes(o: Id[T]): JsValue = {
      JsString(o.value)
    }
  }

  // for serialisation of value classes - typically if you have a `case class UserId(value: Long) extends AnyVal` then it get's serialised to `{"value": 5}`. The below serialises to `5` instead
  // usage: implicit val userIDJson = IDJson(UserID)(UserID.unapply)
  // https://groups.google.com/forum/#!topic/play-framework/zDUxEpEOZ6U
  case class IDJson[I, T](constr: I ⇒ T)(unapply: T ⇒ Option[I])(implicit reads: Reads[I], writes: Writes[I]) extends Reads[T] with Writes[T] {
    def reads(js: JsValue) = js.validate[I] map constr
    def writes(id: T) = Json.toJson(unapply(id))
  }

  trait ApiRequest
  trait ApiResponse
  object ApiFailure {
    import ValidationError._
    case class Failed(validationErrors: List[Invalid] = List())
    implicit val failedFormat = Json.format[Failed]
  }
}
