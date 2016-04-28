package ylabs.play.common.utils

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.User.User

class JWTUtilTest extends WordSpec with Matchers {

  lazy val config = ConfigFactory.load
  val userId = Id[User]()

  "signs and verifies a token" in {
    val jwtUtil = new JWTUtil()
    val token = jwtUtil.issueToken(userId)

    val parsedToken = jwtUtil.parse(token.value)
    parsedToken should be('defined)
    parsedToken.get.getIssuer shouldBe config.getString("jwt.issuer")
    parsedToken.get.getSubject shouldBe userId.value
  }

  "fails to verify if secret was changed" in {
    var jwtUtil = new JWTUtil()
    val token = jwtUtil.issueToken(userId)

    jwtUtil = new JWTUtil() {
      override lazy val config = ConfigFactory.parseString("""
        play.crypto.secret = "changed yet still long enough for hmac to operate"
      """)
    }

    val parsedToken = jwtUtil.parse(token.value)
    parsedToken shouldBe None
  }
}
