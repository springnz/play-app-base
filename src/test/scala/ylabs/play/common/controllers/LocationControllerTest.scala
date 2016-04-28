package ylabs.play.common.controllers

import java.util.Date

import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import springnz.util.Logging
import ylabs.play.common.dal.{GraphDB, LocationRepository}
import ylabs.play.common.models.GeoLocation._
import ylabs.play.common.models.Location._
import ylabs.play.common.models.User
import ylabs.play.common.models.User.{Phone, RegistrationRequest, UserInfoResponse}
import ylabs.play.common.test.LocationTools._
import ylabs.play.common.test.TestTools._
import ylabs.play.common.test.{MyPlaySpec, OneAppPerTestWithOverrides, UserTools}

import scala.concurrent.Await
import scala.concurrent.duration._

class LocationControllerTest extends MyPlaySpec with OneAppPerTestWithOverrides
  with ScalaFutures with BeforeAndAfter with MockitoSugar with Logging {

  val locationRegex = "/location/(.*)".r

  "create" should {

    "return error code in a case of unauthorized request" in new Fixture {
      val request = FakeRequest(POST, "/location")
        .withJsonBody(Json.toJson(testLocation1))

      val response = route(app, request).get
      status(response) shouldBe UNAUTHORIZED
    }

    "create orient entry" in new Fixture {
      val request = FakeRequest(POST, "/location")
        .withJsonBody(Json.toJson(testLocation1))

      val response = route(app, request.withAuth(jwt)).get
      status(response) shouldBe CREATED

      val updateResponse = contentAsJson(response).as[LocationCreatedResponse]
      Await.result(locationRepository.get(updateResponse.id), 5.seconds) match {
        case Some(location) ⇒
          location.latitude shouldBe testLocation1.location.latitude
          location.longitude shouldBe testLocation1.location.longitude

          val oneMinuteAgo = new Date().toInstant.minusSeconds(60)
          withClue("timestamp should be current time") {
            oneMinuteAgo.isBefore(location.timestamp.toInstant) shouldBe true
          }
        case None ⇒ fail("Record does not exist")
      }
    }
  }

  "last" should {

    "return the last location user has been to" in new Fixture {
      submitLocation(jwt)(Location(None, None, Latitude(25.30f), Longitude(30.35f)))
      submitLocation(jwt)(Location(None, None, Latitude(25.31f), Longitude(30.37f)))
      val lastOne = Location(None, None, Latitude(25.32f), Longitude(30.38f))
      submitLocation(jwt)(lastOne)

      val request = FakeRequest(GET, "/location/last")

      val response = route(app, request.withAuth(jwt)).get
      status(response) shouldBe OK

      val receivedResult = contentAsJson(response).asOpt[Location]
      log.info(s"Received last location: $receivedResult")
      receivedResult.nonEmpty shouldBe true
      receivedResult.get.latitude shouldBe lastOne.latitude
      receivedResult.get.longitude shouldBe lastOne.longitude
    }

    "return None in a case when user has never updated his location" in new Fixture {
      val request = FakeRequest(GET, "/location/last")

      val response = route(app, request.withAuth(jwt)).get
      status(response) shouldBe OK

      val receivedResult = contentAsJson(response).asOpt[Location]
      receivedResult.isEmpty shouldBe true
    }

  }

  "nearby" should {

    "return all users who are within 2km of the current user" in new Fixture {
      val baseLatitude = 20.572
      val baseLongitude = 20.572
      val baseLocation = Location(None, None, Latitude(baseLatitude), Longitude(baseLongitude))

      val users = Seq(
        createUserAndLocation(baseLatitude + 0.005, baseLongitude + 0.002), // pass
        createUserAndLocation(baseLatitude + 0.006, baseLongitude + 0.001), // pass
        createUserAndLocation(baseLatitude + 0.009, baseLongitude + 0.001) // pass
        )

      val request = FakeRequest(POST, s"/location/nearby")
        .withJsonBody(Json.toJson(LocationNearbyRequest(baseLocation)))

      val response = route(app, request.withAuth(jwt)).get
      status(response) shouldBe OK

      val responseData = contentAsJson(response).as[LocatedNearbyResponse]
      responseData.results should have size users.size

      responseData.results foreach { r ⇒
        users.find(_._1.phone == r.user.phone) match {
          case Some(resolved) ⇒
            resolved._2.distance(baseLocation, DistanceUnit.Meters) shouldBe r.distance
            resolved._1.name shouldBe r.user.name
            resolved._1.email shouldBe r.user.email
          case None ⇒ fail(s"Unexpected user found in results set: ${r.user.phone}")
        }
      }
    }
  }

  "list" should {
    "return an error code in a case of unauthorized request" in new Fixture {
      val request = FakeRequest(GET, "/location")
      val response = route(app, request).get
      status(response) shouldBe UNAUTHORIZED
    }

    "provide correct listing after some records has been submitted" in new Fixture {
      val locations = (0 until 10) map { _ ⇒ submitRandomLocation(jwt) }

      val request = FakeRequest(GET, "/location")

      val response = route(app, request.withAuth(jwt)).get
      status(response) shouldBe OK

      val receivedResult = contentAsJson(response).as[Seq[Location]]
      receivedResult.size shouldBe locations.size

      locations foreach { l ⇒
        val resolved = receivedResult
          .exists { r ⇒ r.latitude.value == l.latitude.value && r.longitude.value == l.longitude.value }

        resolved shouldBe true
      }
    }
  }

  "suggest" should {
    "return an error code in a case of unauthorized request" in new Fixture {
      val autocomplete = AutoCompleteRequest(
        AutoCompleteQuery("pon"),
        Some(AutoCompleteFilters.Regions),
        Some(List(AutoCompleteComponent(AutoCompleteComponentKeys.Country, "nz")))
      )
      val request = FakeRequest(POST, "/location/suggest")
        .withJsonBody(Json.toJson(autocomplete))
      val response = route(app, request).get
      status(response) shouldBe UNAUTHORIZED
    }

    "pay attention to filters" in new Fixture {
      val autocomplete = AutoCompleteRequest(
        AutoCompleteQuery("pon"),
        Some(AutoCompleteFilters.Regions),
        Some(List(AutoCompleteComponent(AutoCompleteComponentKeys.Country, "nz")))
      )
      val request = FakeRequest(POST, "/location/suggest")
            .withJsonBody(Json.toJson(autocomplete))
            .withAuth(jwt)
      val response = route(app, request).get
      status(response) shouldBe OK
      val receivedResult = contentAsJson(response).as[AutoCompleteResponse]

      receivedResult.results should contain (AddressAndCoordinates(Address("Ponsonby, Auckland, New Zealand"),
        LatLong(Latitude(-36.8473223),Longitude(174.7442273))))
      receivedResult.results should contain (AddressAndCoordinates(Address("Pongakawa, Bay Of Plenty, New Zealand"),
        LatLong(Latitude(-37.836939),Longitude(176.4751917))))
      receivedResult.results should contain (AddressAndCoordinates(Address("Pongaroa, Manawatu-Wanganui, New Zealand"),
        LatLong(Latitude(-40.5414029),Longitude(176.194649))))
      receivedResult.results should contain (AddressAndCoordinates(Address("Ponui Island, Auckland, New Zealand"),
        LatLong(Latitude(-36.8621802),Longitude(175.1842227))))
      receivedResult.results should contain (AddressAndCoordinates(Address("Ponatahi, Wellington, New Zealand"),
        LatLong(Latitude(-41.1040341),Longitude(175.5749038))))
    }
  }

  trait Fixture {
    val locationRepository = app.injector.instanceOf(classOf[LocationRepository])

    val graph = app.injector.instanceOf(classOf[GraphDB]).graph
    graph.V.drop.iterate

    val phoneNumber = generateValidPhone()
    val registration = RegistrationRequest(Phone(phoneNumber), None, User.Name("active location user"))

    val jwt = UserTools.registerUser(registration).jwt

    val testLocation1 = LocationUpdatedRequest(
      Location(None, None, Latitude(50.2532f), Longitude(109.2251f)))

    def createUserAndLocation(latitude: Double, longitude: Double): (UserInfoResponse, Location) = {
      val user = UserTools.registerUser(RegistrationRequest(User.Phone(generateValidPhone()), None, User.Name(s"unknownuseradmin")))

      (user, submitLocation(user.jwt)(Location(None, None, Latitude(latitude), Longitude(longitude))))
    }
  }

}