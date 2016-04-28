package ylabs.play.common.test

import play.api.Application
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.User.{MinimalUser, User, RegistrationRequest, UserInfoResponse}
import ylabs.play.common.test.TestTools._
import ylabs.play.common.utils.JWTUtil.JWT

object UserTools {
  def registerUser(registration: RegistrationRequest)(implicit app: Application): UserInfoResponse = {
    val request = FakeRequest(POST, "/user").withJsonBody(Json.toJson(registration))
    val response = route(app, request).get
    status(response) shouldBe OK
    Json.fromJson[UserInfoResponse](contentAsJson(response)).get
  }

  def getUser(jwt: JWT)(id: Id[User])(implicit app: Application) = {
    val request = FakeRequest(GET, "/user/" + id.value).withAuth(jwt)
    val response = route(app, request).get
    status(response) shouldBe OK
    Json.fromJson[MinimalUser](contentAsJson(response)).get
  }
}
