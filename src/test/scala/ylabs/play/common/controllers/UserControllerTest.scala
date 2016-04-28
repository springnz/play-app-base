package ylabs.play.common.controllers

import com.nimbusds.jwt.JWTClaimsSet
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import ylabs.play.common.dal.{GraphDB, UserRepository}
import ylabs.play.common.models.Helpers.ApiFailure.Failed
import ylabs.play.common.models.User
import ylabs.play.common.models.User._
import ylabs.play.common.models.ValidationError.{Field, Invalid, Reason}
import ylabs.play.common.test.TestTools._
import ylabs.play.common.test.{MyPlaySpec, OneAppPerTestWithOverrides, UserTools}
import ylabs.play.common.utils.{JWTUtil, PhoneValidator}

class UserControllerTest extends MyPlaySpec with OneAppPerTestWithOverrides with ScalaFutures with BeforeAndAfter with MockitoSugar {
  "registration" should {
    "return user info including a valid JWT" in new Fixture {
      val response = UserTools.registerUser(registration)

      response.phone shouldBe validPhone
      response.name shouldBe registration.name

      val jwt = parseJwt(response)
      jwt.getSubject shouldBe response.id.value
      jwt.getStringClaim(JwtClaims.Email) shouldBe registration.email.getOrElse(Email("")).value
      jwt.getStringClaim(JwtClaims.Phone) shouldBe registration.phone.value
      jwt.getStringClaim(JwtClaims.Name) shouldBe registration.name.value
    }

    "allow concurrent registration requests" in new Fixture {
      val registration2 = registration.copy(phone = Phone("0234567890"))
      val request = FakeRequest(POST, "/user").withJsonBody(Json.toJson(registration))
      val request2 = FakeRequest(POST, "/user").withJsonBody(Json.toJson(registration2))

      val response = route(app, request).get
      val response2 = route(app, request2).get

      status(response) shouldBe OK
      status(response2) shouldBe OK

      val user1 = Json.fromJson[UserInfoResponse](contentAsJson(response)).get
      user1.phone shouldBe validPhone
      user1.name shouldBe registration.name
      val user2 = Json.fromJson[UserInfoResponse](contentAsJson(response2)).get
      user2.phone.get shouldBe PhoneValidator.validate(registration2.phone).get
      user2.name shouldBe registration2.name

    }

    "reject missing name" in new Fixture {
      val request = FakeRequest(POST, "/user").withJsonBody(JsObject(Seq(
        "phone" → JsString("0234567890"))))
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
      Json.fromJson[Failed](contentAsJson(response)).get shouldBe Failed(List(Invalid(Field("name"), Reason("Value missing"))))
    }

    "reject invalid phone number" in new Fixture {
      val invalid = RegistrationRequest(User.Phone("01248af"), None, User.Name("invalid nr"))
      val request = FakeRequest(POST, "/user").withJsonBody(Json.toJson(invalid))
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
      Json.fromJson[Failed](contentAsJson(response)).get shouldBe Failed(List(Invalid(Field("phone"), Reason("Invalid NZ mobile number"))))
    }

    "reject non-mobile number" in new Fixture {
      val invalid = RegistrationRequest(User.Phone("031287346"), None, User.Name("non-mobile nr"))
      val request = FakeRequest(POST, "/user").withJsonBody(Json.toJson(invalid))
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
      Json.fromJson[Failed](contentAsJson(response)).get shouldBe Failed(List(Invalid(Field("phone"), Reason("Invalid NZ mobile number"))))
    }
  }

  "allows same user to register multiple times" in new Fixture {
    UserTools.registerUser(registration)
    val newName = User.Name("new name")
    val newUser = UserTools.registerUser(registration.copy(name = newName))

    val repo = app.injector.instanceOf(classOf[UserRepository])
    whenReady(repo.list) { users ⇒
      users should have size 1
      val user = users.head
      user.name shouldBe newName
      user.phone shouldBe validPhone
    }
  }

  "user info endpoint" should {
    "return phone and name for freshly registered user" in new OneUserFixture {
      withAuthVariants(FakeRequest(GET, "/user"), jwt) { response ⇒
        val json = contentAsJson(response)
        val user = Json.fromJson[UserInfoResponse](json).get
        user.phone shouldBe validPhone
        user.name shouldBe registration.name
        user.email shouldBe None
      }
    }

    "allow to update email" in new OneUserFixture {
      val email = User.Email("some@email.com")
      val request = FakeRequest(PATCH, "/user")
        .withJsonBody(
          Json.toJson(UserUpdateRequest(email = Some(email))))

      withAuthVariants(request, jwt) { response ⇒
        val response = route(app, FakeRequest(GET, "/user").withAuth(jwt)).get
        val json = contentAsJson(response)
        val user = Json.fromJson[UserInfoResponse](json).get
        user.phone shouldBe validPhone
        user.name shouldBe registration.name
        user.email shouldBe Some(email)
      }
    }

    "issue new jwt when updating name" in new OneUserFixture {
      val name = User.Name("new name")
      val request = FakeRequest(PATCH, "/user")
        .withJsonBody(
          Json.toJson(UserUpdateRequest(name = Some(name))))

      withAuthVariants(request, jwt) { response ⇒
        val user = Json.fromJson[UserInfoResponse](contentAsJson(response)).get
        user.phone shouldBe validPhone
        user.name shouldBe name
        user.email shouldBe None

        // should issue new JWT
        val newJwt = parseJwt(user)
        newJwt.getSubject shouldBe user.id.value
        newJwt.getStringClaim(JwtClaims.Name) shouldBe name.value
        newJwt.getStringClaim(JwtClaims.Email) shouldBe ""
        newJwt.getStringClaim(JwtClaims.Phone) shouldBe user.phone.get.value
      }
    }

  }

  trait Fixture {
    val registration = RegistrationRequest(User.Phone("+64212345678"), None, User.Name("test name"))
    val validPhone = Some(PhoneValidator.validate(registration.phone).get)
    val graph = app.injector.instanceOf(classOf[GraphDB]).graph

    val jwtUtil = app.injector.instanceOf(classOf[JWTUtil])
    def parseJwt(response: UserInfoResponse): JWTClaimsSet =
      jwtUtil.parse(response.jwt.value).get
    graph.V.drop().iterate
  }

  trait OneUserFixture extends Fixture {
    val jwt = UserTools.registerUser(registration).jwt
  }

}
