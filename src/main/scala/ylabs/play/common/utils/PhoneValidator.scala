package ylabs.play.common.utils

import com.typesafe.config.ConfigFactory
import ylabs.play.common.models.User.Phone

object PhoneValidator {
  def validate(phone: Phone) = {
    lazy val conf = ConfigFactory.load
    val pattern = conf.getString("validation.phone.pattern").r
    val sanitized = sanitize(phone)
    pattern.findFirstMatchIn(sanitized) map (m ⇒ Phone(s"+${conf.getString("validation.phone.prefix.icc")}${m.group(2)}"))
  }

  def sanitize(phone: Phone) =
    phone.value.trim.replaceAll("""[\p{Z}\s-\(\)]+""", "")
}
