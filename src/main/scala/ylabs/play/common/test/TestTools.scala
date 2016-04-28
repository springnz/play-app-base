package ylabs.play.common.test

import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import play.api.Application
import play.api.http.Writeable
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import ylabs.play.common.utils.JWTUtil.JWT

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import scala.util.control.NoStackTrace

object TestTools extends Matchers {

  lazy val config = ConfigFactory.load

  def generateValidPhone() = "+6427" + (0 to 6).map(_ ⇒ Random.nextInt(9).toString).mkString

  implicit class PimpedRequest[A](request: FakeRequest[A]) {
    def withAuth(jwt: JWT) =
      request.withHeaders("Authorization" → s"Bearer ${jwt.value}")

    def withInvalidAuth() =
      request.withAuth(JWT(
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIrNjQyMTQ5OTA4MSIsImlzcyI6ImJhbmpvIn0.P2X30unyuqeR-oFRL6rYPqbO16A0sPIm____INVALID"))
  }

  def withAuthVariants[A: Writeable, B](request: FakeRequest[A], validJwt: JWT)(onOkChecks: Future[Result] ⇒ B)(implicit app: Application): B = {
    val noJwtResponse = route(app, request).get
    withClue("response without a jwt token: ") { status(noJwtResponse) shouldBe UNAUTHORIZED }

    val invalidJwtResponse = route(app, request.withInvalidAuth()).get
    withClue("response with an invalid jwt token: ") { status(invalidJwtResponse) shouldBe UNAUTHORIZED }

    val withValidJwtResponse = route(app, request.withAuth(validJwt)).get
    withClue("response with valid jwt token: ") {
      status(withValidJwtResponse) should (be >= 200 and be < 300)
    }
    onOkChecks(withValidJwtResponse)
  }

  case class AwaitConditionFailedException(message: String, e: Throwable)
    extends RuntimeException(s"$message: [${e.getMessage}]") with NoStackTrace {
    this.setStackTrace(e.getStackTrace)
  }

  def awaitCondition[T](message: String, max: Duration = 10 seconds, interval: Duration = 70 millis)(predicate: ⇒ T) {
    def now: FiniteDuration = System.nanoTime.nanos
    val stop = now + max

    @tailrec
    def poll(nextSleepInterval: Duration) {
      try predicate
      catch {
        case e: Throwable ⇒
          if (now > stop)
            throw new AwaitConditionFailedException(message, e)
          else {
            Thread.sleep(nextSleepInterval.toMillis)
            poll((stop - now) min interval)
          }
      }
    }
    poll(max min interval)
  }
}
