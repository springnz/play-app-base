package ylabs.play.common.test

import play.api.Application
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import ylabs.play.common.models.Location
import ylabs.play.common.test.TestTools._
import ylabs.play.common.utils.JWTUtil

object LocationTools {
  def createRandomLocation() = {
    val la = Location.Latitude(Math.random().asInstanceOf[Float])
    val lo = Location.Longitude(Math.random().asInstanceOf[Float])
    Location.Location(None, None, la, lo)
  }

  def submitRandomLocation(jwt: JWTUtil.JWT)(implicit app: Application): Location.Location = {
    submitLocation(jwt)(createRandomLocation())
  }

  def submitLocation(jwt: JWTUtil.JWT)(location: Location.Location)(implicit app: Application): Location.Location = {
    val request = FakeRequest(POST, "/location")
      .withHeaders(RequestHelpers.DeviceIdHeader)
      .withJsonBody(Json.toJson(Location.LocationUpdatedRequest(location)))

    val response = route(app, request.withAuth(jwt)).get
    status(response) shouldBe CREATED
    location
  }
}
