package ylabs.play.common.models

object HealthCheck {
  case class HealthcheckResponse(errors: Seq[String])
}
