package ylabs.play.common.services

import javax.inject.Singleton

import com.twilio.sdk.TwilioRestClient
import com.typesafe.config.ConfigFactory
import springnz.util.Logging
import springnz.util.Pimpers.FuturePimper
import ylabs.play.common.models.Notify
import ylabs.play.common.models.Sms._
import ylabs.play.common.models.User.Phone

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SmsService extends Logging {
  lazy val config = ConfigFactory.load()
  lazy val client = new TwilioRestClient(config.getString("twilio.accountSid"), config.getString("twilio.authToken"))
  lazy val messageFactory = client.getAccount.getSmsFactory

  lazy val statusCallback = config.getString("twilio.endpoint")

  def send(sms: Sms)(implicit ec: ExecutionContext): Future[String] = Future {
    log.debug(s"attempting to send $sms")
    val endpoint = if(statusCallback.isEmpty) Map( StatusCallback -> statusCallback ) else Map()
    val map = sms.asMap ++ endpoint
    val message = messageFactory.create(map)
    val sid = message.getSid
    log.info(s"sms sent to ${sms.to} - sid=$sid with endpoint=$endpoint")
    sid
  }.withErrorLog(s"send failed for sms($sms).")

  def send(to: Phone, text: Text, endpoint: Option[StatusCallback])(implicit ec: ExecutionContext): Future[String] =
    send(
      Sms(
        From(config.getString("twilio.sender")),
        to,
        text))

  def format(text: Notify.Text, title: Notify.Title): Text =
    Text(title.value + " - " + text.value)
}