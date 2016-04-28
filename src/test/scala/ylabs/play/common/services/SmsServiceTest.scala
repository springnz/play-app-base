package ylabs.play.common.services

import com.twilio.sdk.TwilioRestException
import org.scalatest.{Matchers, WordSpec}
import ylabs.play.common.models.Notify
import ylabs.play.common.models.Notify.Title
import ylabs.play.common.models.Sms.{From, Sms, Text}
import ylabs.play.common.models.User.Phone
import ylabs.play.common.test.OneAppPerTestWithOverrides

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class SmsServiceTest extends WordSpec with OneAppPerTestWithOverrides with Matchers {

  "SMS service" should {
    "check message formatting" in new Fixture {
      val text = smsService.format(Notify.Text("testtext"), Title("testtitle"))
      text shouldBe Text("testtitle - testtext")
    }

    "send a message" in new Fixture {
      val sms = Sms(From("+15005550006"), Phone("+64123456789"), Text("test msg"))
      val sent = Await.result(smsService.send(sms), 9 seconds)
      sent.startsWith("SM") shouldBe true
    }

    "fail to send a message" in new Fixture {
      val sms = Sms(From("+15005550000"), Phone("+64123456789"), Text("test msg"))
      intercept[TwilioRestException] {
        Await.result(smsService.send(sms), 9 seconds)
      }
    }
  }

  trait Fixture {
    val smsService = app.injector.instanceOf(classOf[SmsService])
  }
}
