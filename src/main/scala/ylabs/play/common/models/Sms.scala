package ylabs.play.common.models

import ylabs.play.common.models.User.Phone

object Sms {
  case class Sms(from: From, to: Phone, text: Text) {
    def asMap: Map[String, String] =
      Map("From" → from.value, "To" → to.value, "Body" → text.value)
  }

  case class From(value: String)
  case class Text(value: String)
}