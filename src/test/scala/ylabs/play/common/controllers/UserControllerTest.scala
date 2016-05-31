package ylabs.play.common.controllers

import com.nimbusds.jwt.JWTClaimsSet
import org.mockito.Matchers._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.BeforeAndAfter
import org.mockito.Matchers.{any, eq ⇒ is}
import org.scalatest.concurrent.ScalaFutures
import play.api.inject._
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import ylabs.play.common.dal.{GraphDB, UserRepository}
import ylabs.play.common.models.Helpers.ApiFailure.Failed
import ylabs.play.common.models.Sms.Text
import ylabs.play.common.models.{Sms, User}
import ylabs.play.common.models.User._
import ylabs.play.common.models.ValidationError.{Field, Invalid, Reason}
import ylabs.play.common.services.SmsService
import ylabs.play.common.test.TestTools._
import ylabs.play.common.test.{RequestHelpers, MyPlaySpec, OneAppPerTestWithOverrides, UserTools}
import ylabs.play.common.utils.{JWTUtil, PhoneValidator}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext

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
      val request = FakeRequest(POST, "/user").withHeaders(("Device-Id", "test")).withJsonBody(Json.toJson(registration))
      val request2 = FakeRequest(POST, "/user").withHeaders(("Device-Id", "test")).withJsonBody(Json.toJson(registration2))

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
    val user = UserTools.registerUser(registration, delayDeviceRegistration =  true)
    val newName = User.Name("new name")
    UserTools.registerDevice(user.jwt)

    val newUser = UserTools.registerUser(registration.copy(name = newName), delayDeviceRegistration =  true)

    val repo = app.injector.instanceOf(classOf[UserRepository])
    whenReady(repo.list) { users ⇒
      users should have size 1
      val user = users.head
      user.name shouldBe newName
      user.phone shouldBe validPhone
    }
  }

  "registering device" should {
    "requesting code should trigger sms" in new Fixture {
      val user = UserTools.registerUser(registration, delayDeviceRegistration =  true, delayCodeRequest = true)
      UserTools.requestCode(user.jwt)
      awaitCondition("should send an SMS", max = 1 second) {
        Mockito.verify(mockSmsService).send(
          is(user.phone.get.value).asInstanceOf[Phone],
          is("test name, your confirmation code is: 0000. Thanks!").asInstanceOf[Text])(any[ExecutionContext])
      }
    }

    "should login if tester account, even if not activated" in new Fixture {
      val user = UserTools.registerUser(registration, delayDeviceRegistration =  true, delayCodeRequest = true)
      userRepo.setTesterStatus(user.id, true)
      val info = UserTools.getUser(user.jwt)
      info.id shouldBe user.id
    }

    "properly reset device id" in new Fixture {
      val user = UserTools.registerUser(registration, delayDeviceRegistration =  true, delayCodeRequest = true)
      UserTools.requestCode(user.jwt)

      val request = FakeRequest(POST, "/user/code/request")
        .withHeaders(("Device-Id", "different"))
        .withAuth(user.jwt)
      val response = route(app, request).get
      status(response) shouldBe OK

      val request2 = FakeRequest(POST, "/user/code/register")
        .withAuth(user.jwt)
        .withHeaders(("Device-Id", "different"))
        .withJsonBody(Json.toJson(RegisterDeviceRequest(Code("0000"))))
      val response2 = route(app, request2).get
      status(response2) shouldBe OK
    }

    "be unauthorized if device not registered" in new Fixture {
      val user = UserTools.registerUser(registration, delayDeviceRegistration =  true)
      val request = FakeRequest(GET, "/user")
        .withHeaders(RequestHelpers.DeviceIdHeader)
        .withAuth(user.jwt)
      val response = route(app, request).get
      status(response) shouldBe UNAUTHORIZED
    }

    "be unauthorized if device id changes" in new Fixture {
      val user = UserTools.registerUser(registration)
      val ok = UserTools.getUser(user.jwt)
      val request = FakeRequest(GET, "/user")
        .withHeaders(("Device-Id", "BAD"))
        .withAuth(user.jwt)
      val response = route(app, request).get
      status(response) shouldBe UNAUTHORIZED
    }

    "return error if it has already been registered" in new Fixture{
      val user = UserTools.registerUser(registration, delayDeviceRegistration =  true)
      UserTools.registerDevice(user.jwt)
      val request = FakeRequest(POST, "/user/code/register")
        .withAuth(user.jwt)
        .withHeaders(RequestHelpers.DeviceIdHeader)
        .withJsonBody(Json.toJson(RegisterDeviceRequest(Code("0000"))))
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
      val failed = Json.fromJson[Failed](contentAsJson(response)).get
      failed.validationErrors.head shouldBe Invalid(Field("code"), Reason("Missing"))
    }

    "return error if code is bad" in new Fixture {
      val user = UserTools.registerUser(registration, delayDeviceRegistration =  true)
      val request = FakeRequest(POST, "/user/code/register")
        .withAuth(user.jwt)
        .withHeaders(RequestHelpers.DeviceIdHeader)
        .withJsonBody(Json.toJson(RegisterDeviceRequest(Code("BAD"))))
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
      val failed = Json.fromJson[Failed](contentAsJson(response)).get
      failed.validationErrors.head shouldBe Invalid(Field("code"), Reason("DoesNotMatch"))
    }

    "return error if device id is different" in new Fixture {
      val user = UserTools.registerUser(registration, delayDeviceRegistration =  true)
      val request = FakeRequest(POST, "/user/code/register")
        .withAuth(user.jwt)
        .withHeaders(("Device-Id", "BAD"))
        .withJsonBody(Json.toJson(RegisterDeviceRequest(Code("0000"))))
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
      val failed = Json.fromJson[Failed](contentAsJson(response)).get
      failed.validationErrors.head shouldBe Invalid(Field("deviceId"), Reason("DoesNotMatch"))
    }
  }

  "user info endpoint" should {
    "return phone and name for freshly registered user" in new OneUserFixture {
      withAuthVariants(FakeRequest(GET, "/user").withHeaders(RequestHelpers.DeviceIdHeader), jwt) { response ⇒
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
        .withHeaders(RequestHelpers.DeviceIdHeader)
        .withJsonBody(
          Json.toJson(UserUpdateRequest(email = Some(email))))

      withAuthVariants(request, jwt) { response ⇒
        val response = route(app, FakeRequest(GET, "/user")
          .withHeaders(RequestHelpers.DeviceIdHeader)
          .withAuth(jwt)).get
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
        .withHeaders(RequestHelpers.DeviceIdHeader)
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
  val mockSmsService = mock[SmsService]

  override def overrideModules = Seq(
    bind[SmsService].to(mockSmsService))


  trait Fixture {

    Mockito.reset(mockSmsService)

    val registration = RegistrationRequest(User.Phone("+64212345678"), None, User.Name("test name"))
    val validPhone = Some(PhoneValidator.validate(registration.phone).get)
    val graph = app.injector.instanceOf(classOf[GraphDB]).graph


    val userRepo = app.injector.instanceOf(classOf[UserRepository])

    val jwtUtil = app.injector.instanceOf(classOf[JWTUtil])
    def parseJwt(response: UserInfoResponse): JWTClaimsSet =
      jwtUtil.parse(response.jwt.value).get
    graph.V.drop().iterate
  }

  trait OneUserFixture extends Fixture {
    val jwt = UserTools.registerUser(registration).jwt
  }

}
