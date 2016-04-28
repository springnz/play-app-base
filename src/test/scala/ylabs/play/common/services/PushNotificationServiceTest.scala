package ylabs.play.common.services

import com.amazonaws.services.sns.model.NotFoundException
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import springnz.util.Logging
import ylabs.play.common.models.PushNotification._
import ylabs.play.common.models.User.DeviceEndpoint
import ylabs.play.common.test.TestTools._
import ylabs.play.common.test.{OneAppPerTestWithOverrides, RealPushNotificationsTest}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class PushNotificationServiceTest extends WordSpec with OneAppPerTestWithOverrides with RealPushNotificationsTest with Matchers with BeforeAndAfterAll with Logging {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val config = ConfigFactory.load
  val platform = Platform("android")
  lazy val pushService = app.injector.instanceOf(classOf[PushNotificationService])

  "Push Notification Service" should {

    "create a new endpoint" in new Fixture {
      val endpointAttribs = Await.result(pushService.getEndpointAttributes(endpoint), 3 seconds).getAttributes

      endpoint shouldBe a[DeviceEndpoint]
      endpoint.value.startsWith(config.getString("notification.platform.android"))
      endpointAttribs.get("Token") shouldBe token.value

      deleteEndpoint(endpoint)
    }

    "re-create an endpoint if the current one is missing in AWS SNS" in new Fixture {
      deleteEndpoint(endpoint)
      val newToken = Token("tokentest" + Random.nextInt(Int.MaxValue))
      val newEndpoint = Await.result(pushService.register(platform, newToken, Some(endpoint)), 3 seconds).get

      awaitCondition("make sure an endpoint was re-created", interval = 500 millis) {
        val endpointAttribs = Await.result(pushService.getEndpointAttributes(newEndpoint), 3 seconds).getAttributes
        endpointAttribs.get("Token") shouldBe newToken.value
        endpointAttribs.get("Enabled") shouldBe "true"
      }

      deleteEndpoint(newEndpoint)
    }

    "check and update an existing endpoint" in new Fixture {
      val randInt = Random.nextInt(Int.MaxValue)
      val attribs = Map(("Token", token.value + randInt), ("Enabled", "false"))
      Await.result(pushService.setEndpointAttributes(endpoint, attribs), 3 seconds)
      val endpointAttribs = Await.result(pushService.getEndpointAttributes(endpoint), 3 seconds).getAttributes

      endpointAttribs.get("Token") shouldBe token.value + randInt
      endpointAttribs.get("Enabled") shouldBe "false"

      pushService.register(platform, token, Some(endpoint))
      awaitCondition("make sure an endpoint was updated", interval = 500 millis) {
        val endpointAttribsUpdated = Await.result(pushService.getEndpointAttributes(endpoint), 3 seconds).getAttributes
        endpointAttribsUpdated.get("Token") shouldBe token.value
        endpointAttribsUpdated.get("Enabled") shouldBe "true"
      }

      deleteEndpoint(endpoint)
    }

    "publish a push notification" in new Fixture {
      val notification = Notification(endpoint, Id(123), Name("test"), Title("test"), Text("test"), Link(""), LinkName(""), None)
      val published = Await.result(pushService.publish(notification), 3 seconds)
      published.getMessageId shouldBe a[String]
      deleteEndpoint(endpoint)
    }
  }

  trait Fixture {
    val token = Token("tokentest" + Random.nextInt(Int.MaxValue))
    lazy val endpoint = Await.result(pushService.register(platform, token), 3 seconds).get
    println("ENDPOINT VAL=" + endpoint.value)
  }

  def deleteEndpoint(endpoint: DeviceEndpoint) = {
    pushService.deleteEndpoint(endpoint)
    awaitCondition("make sure an endpoint was deleted", interval = 500 millis) {
      intercept[NotFoundException] {
        Await.result(pushService.getEndpointAttributes(endpoint), 3 seconds)
      }
    }
  }
}
