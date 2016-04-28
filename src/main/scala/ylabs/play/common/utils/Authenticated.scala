package ylabs.play.common.utils

import com.nimbusds.jwt.JWTClaimsSet
import play.api.mvc.Results._
import play.api.mvc.Security.AuthenticatedBuilder
import springnz.util.Logging

object Authenticated extends AuthenticatedBuilder(
  _.headers.get("Authorization").flatMap(AuthenticationHelpers.parseAuthHeader),
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
}