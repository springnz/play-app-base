package ylabs.play.common.services

import ylabs.play.common.models.GeoLocation._
import ylabs.play.common.models.Location.{LatLong, Latitude, Longitude}
import ylabs.play.common.test.{MyPlaySpec, OneAppPerTestWithOverrides}

import scala.concurrent.Await
import scala.concurrent.duration._

class GeoLocationServiceTest extends MyPlaySpec with OneAppPerTestWithOverrides {

  import scala.concurrent.ExecutionContext.Implicits.global

  "simple geolocation lookup" in new Fixture {
    Await.result(geoLocation.lookup(Address("100 Carlton Gore Rd, Auckland, New Zealand")), 5.seconds).get shouldBe
      LatLong(Latitude(-36.8648286), Longitude(174.775974))
  }

  "simple reverse lookup" in new Fixture {
    Await.result(geoLocation.reverseLookup(LatLong(Latitude(-36.8648286), Longitude(174.775974))), 5.seconds).get shouldBe
      Address("100 Carlton Gore Rd, Newmarket, Auckland 1023, New Zealand")
  }

  "autocomplete place" in new Fixture {
    val results = Await.result(geoLocation.suggest(AutoCompleteQuery("pon"),
      Some(AutoCompleteFilters.Regions),
      Some(List(AutoCompleteComponent(AutoCompleteComponentKeys.Country, "nz")))), 5.seconds).get
    results should have size 5
    results should contain (Address("Ponsonby, Auckland, New Zealand"))
    results should contain (Address("Pongaroa, Manawatu-Wanganui, New Zealand"))
    results should contain (Address("Pongakawa, Bay Of Plenty, New Zealand"))
    results should contain (Address("Ponui Island, Auckland, New Zealand"))
    results should contain (Address("Ponatahi, Wellington, New Zealand"))
  }

  "ambiguous location should be resolved to New Zealand" in new Fixture {
    Await.result(geoLocation.lookup(Address("100 Queen Street")), 5.seconds).get shouldBe
      LatLong(Latitude(-36.8467428), Longitude(174.7662273))
  }

  "returns no location for invalid address" in new Fixture {
    Await.result(geoLocation.lookup(Address("this address does not make any sense ZZZZZZZZZZZZZZZZZZZZZZZ")), 5.seconds) shouldBe None
  }

  "invalid latlong should return no address" in new Fixture {
    Await.result(geoLocation.reverseLookup(LatLong(Latitude(-1006.8648286), Longitude(174.775974))), 5.seconds) shouldBe None
  }

  "returns None on error connecting to google service" in {
    val geoLocation = new GeoLocationService {
      override lazy val apiKey = "invalid-key-to-make-connection-fail"
    }
    Await.result(geoLocation.lookup(Address("100 Carlton Gore Rd, Auckland, New Zealand")), 5.seconds) shouldBe None
  }

  trait Fixture {
    val geoLocation = app.injector.instanceOf(classOf[GeoLocationService])
  }
}