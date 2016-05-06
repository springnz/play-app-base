package models

object HealthCheck {
  case class HealthcheckResponse(errors: Seq[String])
}
