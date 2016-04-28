package ylabs.play.common.services

import javax.inject.{Inject, Singleton}

import com.typesafe.config.ConfigFactory
import springnz.util.Logging
import ylabs.play.common.dal.UserRepository
import ylabs.play.common.models.Notify.Notify
import ylabs.play.common.models.User.Phone
import ylabs.play.common.models.{PushNotification, Sms}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class NotifyService @Inject() (repoUser: UserRepository, pushService: PushNotificationService, smsService: SmsService)(implicit ec: ExecutionContext) extends Logging {
  lazy val config = ConfigFactory.load()

  def notify(phone: Phone, notify: Notify, smsFallback: Boolean = false): Future[Unit] = {
    repoUser.getFromPhone(phone) map {
      case Some(user) if user.deviceEndpoint.isDefined && user.deviceEndpoint.get.value.nonEmpty ⇒
        val notifId = notify.notificationId.getOrElse(PushNotification.Id(Random.nextInt(Int.MaxValue)))
        val notif = PushNotification.Notification(
          user.deviceEndpoint.get,
          PushNotification.Id(notifId.value),
          PushNotification.Name(notify.name.value),
          PushNotification.Title(notify.title.value),
          PushNotification.Text(notify.text.value),
          PushNotification.Link(notify.link.map(_.value).getOrElse("")),
          PushNotification.LinkName(notify.linkName.map(_.value).getOrElse("")),
          notify.formattedMessage.map(f => PushNotification.FormattedMessage(f.value)))
        pushService.publish(notif)
        log.debug(s"push notification sent to $phone via ${user.deviceEndpoint.get}")
      case Some(user) if smsFallback ⇒
        val text = notify.formattedMessage match {
          case Some(t) => Sms.Text(t.value)
          case _ => smsService.format(notify.text, notify.title)
        }
        smsService.send(Phone(phone.value), text)
        log.debug(s"sms sent to $phone")
      case None ⇒
        log.error(s"notify failed as the user does not exist ($phone)")
      case user ⇒
        log.info(s"doing nothing for $notify and $user")
    }
  }
}