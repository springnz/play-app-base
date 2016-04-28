package ylabs.play.common.services

import javax.inject.Singleton

import com.twilio.sdk.TwilioRestClient
import com.typesafe.config.ConfigFactory
import springnz.util.Logging
import springnz.util.Pimpers.FuturePimper
import ylabs.play.common.models.Notify
import ylabs.play.common.models.Sms.{From, Sms, Text}
import ylabs.play.common.models.User.Phone

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SmsService extends Logging {
  lazy val config = ConfigFactory.load()
  lazy val client = new TwilioRestClient(config.getString("twilio.accountSid"), config.getString("twilio.authToken"))
  lazy val messageFactory = client.getAccount.getSmsFactory

  def send(sms: Sms)(implicit ec: ExecutionContext): Future[String] = Future {
    log.debug(s"attempting to send $sms")
    val message = messageFactory.create(sms.asMap)
    val sid = message.getSid
    log.info(s"sms sent to ${sms.to} - sid=$sid")
    sid
  }.withErrorLog(s"send failed for sms($sms).")

  def send(to: Phone, text: Text)(implicit ec: ExecutionContext): Future[String] =
    send(
      Sms(
        From(config.getString("twilio.sender")),
        to,
        text))

  def format(text: Notify.Text, title: Notify.Title): Text =
    Text(title.value + " - " + text.value)
}