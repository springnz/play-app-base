package ylabs.play.common.services

import org.mockito.Matchers.{any, eq ⇒ is}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.inject._
import ylabs.play.common.dal.UserRepository
import ylabs.play.common.models.PushNotification._
import ylabs.play.common.models.User.{DeviceEndpoint, Phone, Status}
import ylabs.play.common.models.{Notify, PushNotification, Sms, User}
import ylabs.play.common.test.OneAppPerTestWithOverrides
import ylabs.play.common.test.TestTools._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

class NotifyServiceTest extends WordSpec with OneAppPerTestWithOverrides with Matchers with MockitoSugar {

  "Notify Service" should {

    "send an sms to an invited user" in new InvitedUserFixture {
      notifyService.notify(phone, notify1, smsFallback = true)
      awaitCondition("should send an SMS", max = 1 second) {
        Mockito.verify(mockSmsService).send(
          is(phone.value).asInstanceOf[Phone],
          is(Sms.Text("sms test text")))(any[ExecutionContext])
      }

      Mockito.verify(pushNotificationService, never).publish(any[Notification])(any[ExecutionContext])
    }

    "send a notification to a registered user" in new RegisteredUserFixture {
      notifyService.notify(phone, notify1)
      awaitCondition("should send a push notification", max = 1 second) {
        Mockito.verify(pushNotificationService).publish(is(notification))(any[ExecutionContext])
      }

      Mockito.verify(mockSmsService, never).send(any[Sms.Sms])(any[ExecutionContext])
    }

    "not send anything to an invited user without sms fallback" in new InvitedUserFixture {
      notifyService.notify(phone, notify1)

      Mockito.verify(pushNotificationService, never).publish(any[Notification])(any[ExecutionContext])

      awaitCondition("should not send an SMS", max = 1 second) {
        Mockito.verify(mockSmsService, never).send(
          is(phone.value).asInstanceOf[Phone],
          is(Sms.Text("sms test text")))(any[ExecutionContext])
      }
    }

    "not send anything to non-existent user" in new Fixture {
      notifyService.notify(phone, notify1)
      Mockito.verify(pushNotificationService, never).publish(any[Notification])(any[ExecutionContext])
      Mockito.verify(mockSmsService, never).send(
        is(phone.value).asInstanceOf[Phone],
        is(Sms.Text("sms test text")))(any[ExecutionContext])
    }

  }

  val mockSmsService = mock[SmsService]

  override def overrideModules = Seq(
    bind[SmsService].to(mockSmsService))

  trait Fixture {
    Mockito.reset(pushNotificationService)
    Mockito.reset(mockSmsService)
    when(mockSmsService.format(any[Notify.Text], any[Notify.Title])).thenReturn(Sms.Text("sms test text"))

    val repoUser = app.injector.instanceOf(classOf[UserRepository])

    val name = User.Name("test name")
    val phone = User.Phone("+64123456789")
    val notifyService = app.injector.instanceOf(classOf[NotifyService])

    val notify1 = Notify.Notify(
      Notify.Name("invite"),
      Notify.Title("test title"),
      Notify.Text("test text"),
      Some(Notify.Link("test link")),
      Some(Notify.LinkName("test link name")),
      Some(PushNotification.Id(12345)))

    val notification = Notification(
      DeviceEndpoint("endpoint123"),
      Id(12345),
      Name("invite"),
      Title("test title"),
      Text("test text"),
      Link("test link"),
      LinkName("test link name"),
      None)
  }

  trait InvitedUserFixture extends Fixture {
    Await.result(repoUser.createFromPhone(phone, name, Status(User.Invited), Some(DeviceEndpoint(""))), 3 seconds)
  }

  trait RegisteredUserFixture extends Fixture {
    Await.result(repoUser.createFromPhone(phone, name, Status(User.Registered), Some(DeviceEndpoint("endpoint123"))), 3 seconds)
  }

}
