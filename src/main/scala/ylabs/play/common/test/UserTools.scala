package ylabs.play.common.test

import play.api.Application
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.User._
import ylabs.play.common.test.TestTools._
import ylabs.play.common.utils.JWTUtil.JWT

object UserTools {
  def registerUser(registration: RegistrationRequest, delayDeviceRegistration: Boolean = false)(implicit app: Application): UserInfoResponse = {
    val request = FakeRequest(POST, "/user")
      .withHeaders(RequestHelpers.DeviceIdHeader)
      .withJsonBody(Json.toJson(registration))
    val response = route(app, request).get
    status(response) shouldBe OK
    val user = Json.fromJson[UserInfoResponse](contentAsJson(response)).get
    if(!delayDeviceRegistration)
      registerDevice(user.jwt)
    else user
  }

  def requestCode(jwt: JWT)(implicit app: Application) = {
    val request = FakeRequest(POST, "/user/code/request")
      .withHeaders(RequestHelpers.DeviceIdHeader)
      .withAuth(jwt)
    val response = route(app, request).get
    status(response) shouldBe OK
  }

  def registerDevice(jwt: JWT)(implicit app: Application) = {
    val request = FakeRequest(POST, "/user/code/register")
      .withAuth(jwt)
      .withHeaders(RequestHelpers.DeviceIdHeader)
      .withJsonBody(Json.toJson(RegisterDeviceRequest(Code("0000"))))
    val response = route(app, request).get
    status(response) shouldBe OK
    Json.fromJson[UserInfoResponse](contentAsJson(response)).get
  }

  def getUser(jwt: JWT)(implicit app: Application) = {
    val request = FakeRequest(GET, "/user")
      .withHeaders(RequestHelpers.DeviceIdHeader)
      .withAuth(jwt)
    val response = route(app, request).get
    status(response) shouldBe OK
    Json.fromJson[UserInfoResponse](contentAsJson(response)).get
  }
}
