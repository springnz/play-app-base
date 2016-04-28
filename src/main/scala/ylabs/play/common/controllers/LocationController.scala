package ylabs.play.common.controllers

import javax.inject.Inject

import akka.util.Timeout
import play.api.libs.json.Json
import play.api.mvc.Controller
import ylabs.play.common.dal.LocationRepository
import ylabs.play.common.models.GeoLocation.{ ReverseLookupRequest, AddressAndCoordinates, AutoCompleteRequest, AutoCompleteResponse }
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.Location._
import ylabs.play.common.models.User
import ylabs.play.common.services.GeoLocationService
import ylabs.play.common.utils.{Authenticated, FailureType}

import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.duration._

object LocationController {
  lazy val nearbyDistanceThreshold = 2000f
}

class LocationController @Inject() (locationRepository: LocationRepository,
    geoLocation: GeoLocationService)(implicit ec: ExecutionContext) extends Controller {

  import LocationController._
  implicit val timeout: Timeout = 5.seconds

  def update() = Authenticated.async(parse.json[LocationUpdatedRequest]) { request ⇒
    val user = Id[User.User](request.user.getSubject)

    locationRepository.create(user, request.body.location)
      .map(r ⇒ Created(Json.toJson(LocationCreatedResponse(r))))
      .recover { case FailureType.RecordNotFound ⇒ NotFound(user.value) }
  }

  def last() = Authenticated.async { request ⇒
    val user = Id[User.User](request.user.getSubject)
    locationRepository.lastUserLocation(user) map (l ⇒ Ok(Json.toJson(l)))
  }

  def nearby() = Authenticated.async(parse.json[LocationNearbyRequest]) { request ⇒
    locationRepository.listNearby(Distance(nearbyDistanceThreshold, DistanceUnit.Meters), request.body.location) map { distancesList ⇒
      Ok(Json.toJson(
        LocatedNearbyResponse(distancesList.toSeq map {
          case (user, distance) ⇒ UserDistance(user.toMinimalUser, distance)
        })))
    }
  }

  def suggest() = Authenticated.async(parse.json[AutoCompleteRequest]) { request ⇒
    geoLocation.suggest(request.body.query, request.body.filter, request.body.components) flatMap {
      case Some(locations) ⇒
        val lookupFuture = Future.sequence(locations.map(geoLocation.lookup))
        lookupFuture.map { latlons ⇒
          val results = latlons.zip(locations).collect {
            case (Some(latlong), address) ⇒ AddressAndCoordinates(address, latlong)
          }
          Ok(Json.toJson(AutoCompleteResponse(results)))
        }

      case None ⇒
        Future.successful(InternalServerError)
    }
  }

  def reverseLookup() = Authenticated.async(parse.json[ReverseLookupRequest]) { request ⇒
    geoLocation.reverseLookup(request.body.coordinates) map {
      case Some(address) ⇒
        Ok(Json.toJson(AddressAndCoordinates(address, request.body.coordinates)))
      case None ⇒
        Ok
    }
  }

  def list() = Authenticated.async { request ⇒
    val userId = Id[User.User](request.user.getSubject)
    locationRepository.listByUser(userId) map (l ⇒ Ok(Json.toJson(l)))
  }

}
