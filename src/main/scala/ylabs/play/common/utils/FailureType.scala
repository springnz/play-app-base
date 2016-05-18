package ylabs.play.common.utils

trait FailureType extends Throwable
object FailureType {
  case object RecordNotFound extends FailureType
  case object DeviceIdDoesNotMatch extends FailureType
  case object DeviceCodeDoesNotMatch extends FailureType
  case object DeviceCodeMissing extends FailureType
  case object DeviceNotActivated extends FailureType
}
