package ylabs.play.common.utils

sealed trait FailureType extends Throwable
object FailureType {
  case object RecordNotFound extends FailureType
}
