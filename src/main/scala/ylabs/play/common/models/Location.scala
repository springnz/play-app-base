package ylabs.play.common.models

import java.util.Date

import gremlin.scala._
import play.api.libs.json._
import ylabs.play.common.models.Helpers.{ApiRequest, ApiResponse, IDJson, Id}

object Location {

  val Label = "location"
  val BelongsTo = "locationBelongsTo"
  object Properties {
    object Id extends Key[String]("id")
    object Timestamp extends Key[Date]("timestamp")
    object LocationLatitude extends Key[Double]("latitude")
    object LocationLongitude extends Key[Double]("longitude")
  }

  sealed trait DistanceUnit {
    def convert(distance: Distance): Distance
  }

  object DistanceUnit {
    case object Meters extends DistanceUnit {
      def convert(distance: Distance) = distance.distanceUnit match {
        case DistanceUnit.Meters ⇒ distance
        case DistanceUnit.Kilometers => Distance(distance.value*1000, DistanceUnit.Meters)
      }
    }

    case object Kilometers extends DistanceUnit {
      def convert(distance: Distance) = distance.distanceUnit match {
        case DistanceUnit.Kilometers ⇒ distance
        case DistanceUnit.Meters => Distance(distance.value/1000, DistanceUnit.Kilometers)
      }
    }
  }

  case class Latitude(value: Double) extends AnyVal
  case class Longitude(value: Double) extends AnyVal
  case class LatLong(latitude: Latitude, longitude: Longitude) {
    def toLocation = Location(None, None, latitude, longitude)
  }

  case class Distance(value: Double, distanceUnit: DistanceUnit) {
    def -(distance: Distance) = {
      distanceUnit.convert(distance)
    }
  }

  case class UserDistance(user: User.MinimalUser, distance: Distance)

  object Location {
    val earthRadius = 6373
    val metersInKilometer = 1000
  }

  @label("location")
  case class Location(
      id: Option[Id[Location]],
      user: Option[Id[User.User]],
      latitude: Latitude,
      longitude: Longitude,
      timestamp: Date = new Date()) {

    def toLatLon = LatLong(latitude, longitude)

    def distance(target: Location, distanceUnit: DistanceUnit): Distance = {
      /**
        * @see Original algorithm: http://andrew.hedges.name/experiments/haversine/
        */
      val sourceLatitude = latitude.value.toRadians
      val targetLatitude = target.latitude.value.toRadians
      val sourceLongitude = longitude.value.toRadians
      val targetLongitude = target.longitude.value.toRadians

      val longitudeDiff = targetLongitude - sourceLongitude
      val latitudeDiff = targetLatitude - sourceLatitude

      val a = Math.pow(Math.sin(latitudeDiff / 2), 2) + Math.cos(sourceLatitude) * Math.cos(targetLatitude) * Math.pow(Math.sin(longitudeDiff / 2), 2)
      val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

      val distanceInMeters = Distance(Location.earthRadius * c * Location.metersInKilometer, DistanceUnit.Meters)
      distanceUnit.convert(distanceInMeters)
    }
  }

  def apply(v: Vertex) =
    Location(
      v.property(Properties.Id).toOption.map(i ⇒ Id[Location](i)),
      v.out(BelongsTo).headOption map (h ⇒ User.apply(h).id),
      Latitude(v.value2(Properties.LocationLatitude)),
      Longitude(v.value2(Properties.LocationLongitude)),
      v.value2(Properties.Timestamp))

  case class LocationNearbyRequest(location: Location) extends ApiRequest
  case class LocationUpdatedRequest(location: Location) extends ApiRequest
  case class LocatedNearbyResponse(results: Seq[UserDistance]) extends ApiResponse
  case class LocationCreatedResponse(id: Id[Location]) extends ApiResponse

  implicit val distanceUnitFormat = new Format[DistanceUnit] {
    override def reads(json: JsValue): JsResult[DistanceUnit] = {
      json.validate[String] map {
        case "DistanceUnit.Meters"     ⇒ DistanceUnit.Meters
        case "DistanceUnit.Kilometers" ⇒ DistanceUnit.Kilometers
        case _                         ⇒ throw new IllegalArgumentException("unknown type")
      }
    }

    override def writes(o: DistanceUnit): JsValue = {
      o match {
        case DistanceUnit.Meters ⇒ JsString("DistanceUnit.Meters")
        case DistanceUnit.Kilometers => JsString("DistanceUnit.Kilometers")
      }
    }
  }

  implicit val latitudeFormat = IDJson(Latitude.apply)(Latitude.unapply)
  implicit val longitudeFormat = IDJson(Longitude.apply)(Longitude.unapply)
  implicit val latlongFormat = Json.format[LatLong]
  implicit val locationFormat = Json.format[Location]
  implicit val distanceFormat = Json.format[Distance]
  implicit val userDistanceFormat = Json.format[UserDistance]

  implicit val locatedNearbyResponseFormat = Json.format[LocatedNearbyResponse]
  implicit val locationNearbyRequestFormat = Json.format[LocationNearbyRequest]
  implicit val locationCreatedResponseFormat = Json.format[LocationCreatedResponse]
  implicit val locationUpdatedRequestFormat = Json.format[LocationUpdatedRequest]

}
