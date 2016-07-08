package ylabs.play.common.utils

import javax.inject.Inject

import com.nimbusds.jwt.JWTClaimsSet
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import play.api.mvc.Security.AuthenticatedBuilder
import springnz.util.Logging
import ylabs.play.common.dal.UserRepository
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.User
import ylabs.play.common.models.User.DeviceId
import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.duration._

class Authenticated @Inject () (userRepository: UserRepository)(implicit ec: ExecutionContext) extends AuthenticatedBuilder( ctx => {
  val deviceId = ctx.headers.get("Device-Id").map(DeviceId)

  Await.result(ctx.headers.get("Authorization")
    .flatMap(AuthenticationHelpers.parseAuthHeader)
    .flatMap(jwt => deviceId.map(dId => AuthenticationHelpers.getUser(jwt, dId, userRepository, checkActivated = true)))
    .getOrElse(Future.successful(None)), 5.seconds)
  },
  onUnauthorized = { _ ⇒ Unauthorized })

class NonActiveAuthenticated @Inject () (userRepository: UserRepository)(implicit ec: ExecutionContext) extends AuthenticatedBuilder( ctx => {
    val deviceId = ctx.headers.get("Device-Id").map(DeviceId)
    Await.result(ctx.headers.get("Authorization")
      .flatMap(AuthenticationHelpers.parseAuthHeader)
      .flatMap(jwt => deviceId.map(dId => AuthenticationHelpers.getUser(jwt, dId, userRepository, checkActivated = false)))
      .getOrElse(Future.successful(None)), 5.seconds)
  },
  onUnauthorized = { _ ⇒ Unauthorized })

object AuthenticationHelpers extends Logging {
  val AuthHeaderRegexp = """Bearer (.*)""".r
  val jwtUtil = new JWTUtil

  def parseAuthHeader(authHeader: String): Option[JWTClaimsSet] = authHeader match {
    case AuthHeaderRegexp(b64Token) ⇒ jwtUtil.parse(b64Token)
    case _ ⇒
      log.warn(s"invalid jwt auth header: $authHeader")
      None
  }

  def getUser(jwt: JWTClaimsSet, deviceId: DeviceId, userRepository: UserRepository, checkActivated: Boolean)(implicit ec: ExecutionContext): Future[Option[User.User]] = {
    val id = Id[User.User](jwt.getSubject)
    userRepository.get(id)/*.map({
      case Some(u) if !checkActivated || u.isTester || (u.deviceId.isDefined && u.deviceActivated && u.deviceId.get == deviceId) => Some(u)
      case _ => None
    })*/
  }
}