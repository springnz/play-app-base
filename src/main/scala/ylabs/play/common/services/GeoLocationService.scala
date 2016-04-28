package ylabs.play.common.services

import java.util.concurrent.TimeUnit
import javax.inject.Singleton

import com.google.maps.model.{ComponentFilter, LatLng, PlaceAutocompleteType}
import com.google.maps.{GeoApiContext, GeocodingApi, PlacesApi}
import com.typesafe.config.ConfigFactory
import springnz.util.Logging
import ylabs.play.common.models.GeoLocation.{Address, AutoCompleteComponent, AutoCompleteFilter, AutoCompleteQuery}
import ylabs.play.common.models.Location.{LatLong, Latitude, Longitude}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class GeoLocationService extends Logging {
  lazy val config = ConfigFactory.load()
  lazy val apiKey = config.getString("google.apiKey")

  def newContext = new GeoApiContext()
    .setApiKey(apiKey)
    .setQueryRateLimit(10)
    .setConnectTimeout(1, TimeUnit.SECONDS)
    .setReadTimeout(1, TimeUnit.SECONDS)
    .setWriteTimeout(1, TimeUnit.SECONDS)

  def newRequest = {
    GeocodingApi.newRequest(newContext)
  }

  def lookup(address: Address)(implicit ec: ExecutionContext): Future[Option[LatLong]] =
    Future {
      Try {
        newRequest.address(address.value + " New Zealand").await.toList
      } match {
        case Success(geoLocationResults) ⇒
          log.info(s"geolocation results for $address: $geoLocationResults")
          val result = geoLocationResults.headOption //we assume the list is ordered, just take the first result
            .map(_.geometry.location)
            .map { latLong ⇒ LatLong(Latitude(latLong.lat), Longitude(latLong.lng)) }
          log.info(s"returning geolocation for $address: $result")
          result
        case Failure(t) ⇒
          log.error("unable to lookup geolocation", t)
          None
      }
    }

  def suggest(input: AutoCompleteQuery,
              types: Option[AutoCompleteFilter] = None,
              components: Option[List[AutoCompleteComponent]] = None)
             (implicit ec: ExecutionContext): Future[Option[List[Address]]] = {
    Future {
      Try {
        val componentFilters = components.getOrElse(List()).map(c => c.key match {
          case "route" => ComponentFilter.route(c.value)
          case "locality" => ComponentFilter.locality(c.value)
          case "administrative_area" => ComponentFilter.administrativeArea(c.value)
          case "postal_code" => ComponentFilter.postalCode(c.value)
          case "country" => ComponentFilter.country(c.value)
          case bad => throw new Exception(s"Don't know how to process component $bad")
        })

        val request = types match {
          case Some(filter) =>
            val autoCompleteType = PlaceAutocompleteType.valueOf(filter.value)
            PlacesApi.placeAutocomplete(newContext, input.value)
              .`type`(autoCompleteType)
              .components(componentFilters: _*)
              .await()
              .toList
          case None => PlacesApi.placeAutocomplete(newContext, input.value)
            .components(componentFilters: _*)
            .await()
            .toList
        }

        request
      } match {
        case Success(autoCompleteResults) =>
          log.info(s"autocomplete results for $input: $autoCompleteResults")
          Some(autoCompleteResults.map(a =>  Address(a.description)))
        case Failure(t) =>
          log.error("unable to lookup autocomplete", t)
          None
      }
    }
  }

  def reverseLookup(latlong: LatLong)(implicit ec: ExecutionContext): Future[Option[Address]] =
    Future {
      Try {
        newRequest.latlng(new LatLng(latlong.latitude.value, latlong.longitude.value)).await().headOption
      } match {
        case Success(geoLocationResult) =>
          log.info(s"gelocation results for $latlong: $geoLocationResult")
          geoLocationResult map { res => Address(res.formattedAddress) }
        case Failure(t) =>
          log.error("unable to lookup geolocation", t)
          None
      }
    }
}