package ylabs.play.common.models

import gremlin.scala.Key
import play.api.libs.json.Json
import ylabs.play.common.models.Helpers.{ApiRequest, IDJson}
import ylabs.play.common.models.Location.LatLong

object GeoLocation {

  case class Address(value: String) extends AnyVal
  case class AutoCompleteQuery(value: String) extends AnyVal
  case class AutoCompleteFilter(value: String) extends AnyVal
  object AutoCompleteFilters {
    val Geocode = AutoCompleteFilter("GEOCODE")
    val Address = AutoCompleteFilter("ADDRESS")
    val Establishment = AutoCompleteFilter("ESTABLISHMENT")
    val Regions = AutoCompleteFilter("REGIONS")
    val Cities = AutoCompleteFilter("CITIES")
  }

  case class AutoCompleteComponent(key: String, value: String)
  object AutoCompleteComponentKeys {
    val Route = "route"
    val Locality = "locality"
    val AdministrativeArea = "administrative_area"
    val PostalCode = "postal_code"
    val Country = "country"

  }

  case class AddressAndCoordinates(address: Address, coordinates: LatLong)
 
  case class AutoCompleteRequest(query: AutoCompleteQuery,
                                 filter: Option[AutoCompleteFilter],
                                 components: Option[List[AutoCompleteComponent]]) extends ApiRequest

  case class ReverseLookupRequest(coordinates: LatLong) extends ApiRequest

  case class AutoCompleteResponse(results: List[AddressAndCoordinates]) extends  ApiRequest

  val LastAddress = Key[String]("lastAddress")
  val LastLat = Key[Double]("lastLat")
  val LastLong = Key[Double]("lastLong")

  implicit val addressFormat = IDJson(Address.apply)(Address.unapply)
  implicit val autocompleteFormat = IDJson(AutoCompleteQuery.apply)(AutoCompleteQuery.unapply)
  implicit val autocompleteFilterFormat = IDJson(AutoCompleteFilter.apply)(AutoCompleteFilter.unapply)

  implicit val componentFormat = Json.format[AutoCompleteComponent]
  implicit val autocompleteRequestFormat = Json.format[AutoCompleteRequest]
  implicit val autoCompleteResultFormat = Json.format[AddressAndCoordinates]
  implicit val autoCompleteResponseFormat = Json.format[AutoCompleteResponse]
  implicit val reverseLookupRequestFormat = Json.format[ReverseLookupRequest]

}
