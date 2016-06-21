package ylabs.play.common.services

import javax.inject.Singleton

import com.amazonaws.regions.Regions
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model._
import com.typesafe.config.ConfigFactory
import springnz.util.Logging
import springnz.util.Pimpers.{FuturePimper, TryPimper}
import ylabs.play.common.models.PushNotification._
import ylabs.play.common.models.User.DeviceEndpoint

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Try

@Singleton
class PushNotificationService extends Logging {
  lazy val config = ConfigFactory.load().getConfig("notification")
  lazy val client: AmazonSNSClient = new AmazonSNSClient()
    .withRegion(Regions.fromName(config.getString("region")))

  def publish(notification: Notification)(implicit ec: ExecutionContext): Future[PublishResult] = Future {
    val formattedMessage = notification.formattedMessage.getOrElse(formatMessage(notification))
    val req = new PublishRequest()
      .withMessageStructure("json")
      .withTargetArn(notification.endpoint.value)
      .withMessage(formattedMessage.value)

    log.debug(s"push notification content $formattedMessage")

    client.publish(req)

  }.withErrorLog(s"publish failed for endpoint(${notification.endpoint.value}) and message(${notification.text.value}).")

  def register(platform: Platform, token: Token, currentEndpoint: Option[DeviceEndpoint] = None)(implicit ec: ExecutionContext): Future[Option[DeviceEndpoint]] = {

    val platformEndpoint = platform.value.isEmpty match {
      case false ⇒ PlatformEndpoint(config.getString("platform." + platform.value.toLowerCase))
      case true  ⇒ PlatformEndpoint("")
    }

    currentEndpoint match {
      case Some(endpoint) ⇒ checkEndpoint(platformEndpoint, endpoint, token)
      case None           ⇒ createEndpoint(platformEndpoint, token)
    }

  }.withErrorLog(s"register failed for platform($platform), token($token) and currentEndpoint($currentEndpoint).")

  def createEndpoint(platformEndpoint: PlatformEndpoint, token: Token)(implicit ec: ExecutionContext): Future[Option[DeviceEndpoint]] = Future {

    val req = new CreatePlatformEndpointRequest()
      .withPlatformApplicationArn(platformEndpoint.value)
      .withToken(token.value)

    val endpoint = Try {
      DeviceEndpoint(client.createPlatformEndpoint(req).getEndpointArn)
    }.withErrorLog(s"createEndpoint failed for platformAppEndpoint($platformEndpoint) and token($token).")

    endpoint.toOption

  }.withErrorLog(s"createEndpoint failed for platformAppEndpoint($platformEndpoint) and token($token).")

  def checkEndpoint(platformAppEndpoint: PlatformEndpoint, endpoint: DeviceEndpoint, token: Token)(implicit ec: ExecutionContext): Future[Option[DeviceEndpoint]] = {

    getEndpointAttributes(endpoint).map { res ⇒
      val attribs = res.getAttributes
      if (!attribs.get("Token").equals(token.value) || !attribs.get("Enabled").equalsIgnoreCase("true"))
        updateEndpoint(endpoint, token)
      Some(endpoint)
    }.recover { case _ ⇒ Await.result(createEndpoint(platformAppEndpoint, token), 3 seconds) }

  }.withErrorLog(s"checkEndpoint failed for endpoint($endpoint) and token($token).")

  def updateEndpoint(endpoint: DeviceEndpoint, token: Token)(implicit ec: ExecutionContext): Future[Unit] = {

    val attribs = Map(("Token", token.value), ("Enabled", "true"))
    setEndpointAttributes(endpoint, attribs)

  }.withErrorLog(s"updateEndpoint failed for endpoint($endpoint) and token($token).")

  def deleteEndpoint(endpoint: DeviceEndpoint)(implicit ec: ExecutionContext): Future[Unit] = Future {
    val req = new DeleteEndpointRequest()
      .withEndpointArn(endpoint.value)
    client.deleteEndpoint(req)

  }.withErrorLog(s"deleteEndpoint failed for endpoint($endpoint).")

  def getEndpointAttributes(endpoint: DeviceEndpoint)(implicit ec: ExecutionContext): Future[GetEndpointAttributesResult] = Future {
    val req = new GetEndpointAttributesRequest()
      .withEndpointArn(endpoint.value)
    client.getEndpointAttributes(req)

  }.withErrorLog(s"getEndpointAttributes failed for endpoint($endpoint).")

  def setEndpointAttributes(endpoint: DeviceEndpoint, attribs: Map[String, String])(implicit ec: ExecutionContext): Future[Unit] = Future {
    val req = new SetEndpointAttributesRequest()
      .withEndpointArn(endpoint.value)
      .withAttributes(attribs)

    client.setEndpointAttributes(req)

  }.withErrorLog(s"setEndpointAttributes failed for endpoint($endpoint) and attribs($attribs).")

  def formatMessage(notification: Notification): FormattedMessage = {
    FormattedMessage(s"""
       |{
       |"default":"${notification.title.value} - ${notification.text.value}",
       |"APNS":"{\\"aps\\":{
       |\\"content-available\\": 1,
       |\\"alert\\":\\"${notification.text.value}\\",
       |\\"name\\":\\"${notification.name.value}\\",
       |\\"title\\":\\"${notification.title.value}\\",
       |\\"text\\":\\"${notification.text.value}\\",
       |\\"link\\":\\"${notification.link.value}\\",
       |\\"linkName\\":\\"${notification.linkName.value}\\"
       |}}",
       |"GCM":"{\\"data\\":{
       |\\"style\\": \\"inbox\\",
       |\\"summaryText\\": \\"There are %n% notifications\\",
       |\\"notId\\":\\"${notification.id.value}\\",
       |\\"title\\":\\"${notification.title.value}\\",
       |\\"message\\":\\"${notification.text.value}\\",
       |\\"text\\":\\"${notification.text.value}\\",
       |\\"name\\":\\"${notification.name.value}\\",
       |\\"link\\":\\"${notification.link.value}\\",
       |\\"linkName\\":\\"${notification.linkName.value}\\"
       |}}"
       |}
    """.stripMargin.replaceAll("\n", ""))
  }
}
