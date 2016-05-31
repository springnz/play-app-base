package ylabs.play.common.models

import java.util.Date

import gremlin.scala._
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.Sms._
import ylabs.play.common.models.User.Phone


object StoredSms {
  case class StoredSms(id: Id[StoredSms], sms: SmsStatusChanged, timesTried: Int, created: Date, lastTried: Date) {
    def toSms = Sms.Sms(sms.from, sms.to, sms.body)
  }

  val Label = "StoredSms"

  object Properties {
    object Id extends Key[String]("id")
    object From extends Key[String]("from")
    object To extends Key[String]("to")
    object Body extends Key[String]("body")
    object Status extends Key[String]("status")
    object ErrorCode extends Key[String]("errorCode")

    object TimesTried extends Key[Int]("timesTried")

    object Created extends Key[Date]("created")
    object LastTried extends Key[Date]("lastTried")
  }

  def apply(v: Vertex) = StoredSms(
    Id[StoredSms](v.value2(Properties.Id)),
    SmsStatusChanged(
      From(v.value2(Properties.From)),
      Phone(v.value2(Properties.To)),
      Text(v.value2(Properties.Body)),
      Status(v.value2(Properties.Status)),
      ErrorCode(v.value2(Properties.ErrorCode))
    ),
    v.value2(Properties.TimesTried),
    v.value2(Properties.Created),
    v.value2(Properties.LastTried)
  )
}
