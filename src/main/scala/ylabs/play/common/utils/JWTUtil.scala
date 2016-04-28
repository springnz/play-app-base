package ylabs.play.common.utils

import javax.inject.Singleton

import com.nimbusds.jose._
import com.nimbusds.jose.crypto._
import com.nimbusds.jwt._
import com.typesafe.config.ConfigFactory
import ylabs.play.common.models.Helpers.{IDJson, Id}
import ylabs.play.common.models.User.User

object JWTUtil {
  case class JWT(value: String) extends AnyVal
  implicit val jwtFormat = IDJson(JWT)(JWT.unapply)
}

@Singleton
class JWTUtil {
  lazy val config = ConfigFactory.load
  lazy val issuer = config.getString("jwt.issuer")
  lazy val serverKey = config.getString("play.crypto.secret").getBytes
  lazy val signer = new MACSigner(serverKey)

  def issueToken(userId: Id[User], claims: (String, Any)*): JWTUtil.JWT = {
    val claimsSet = {
      var builder = new JWTClaimsSet.Builder()
        .subject(userId.value)
        .issuer(issuer)
      claims foreach {
        case (key, value) ⇒
          builder = builder.claim(key, value)
      }
      builder.build
    }

    val signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet)
    signedJWT.sign(signer)
    JWTUtil.JWT(signedJWT.serialize())
  }

  def parse(b64Token: String): Option[JWTClaimsSet] = {
    val token = SignedJWT.parse(b64Token)
    if (isValid(token)) Some(token.getJWTClaimsSet)
    else None
  }

  def isValid(token: SignedJWT): Boolean = {
    val verifier = new MACVerifier(serverKey)
    token.verify(verifier)
  }
}