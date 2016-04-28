package ylabs.play.common.utils

import org.scalatest.{Matchers, WordSpec}
import ylabs.play.common.models.Location._

class DistanceTest extends WordSpec with Matchers {

  "compute distance between to locations" in {
    Math.ceil(distance(21, 25, 26, 26).value) shouldBe 565418
    Math.ceil(distance(20.572, 20.572, 20.571, 20.571).value) shouldBe 153
    Math.ceil(distance(0, 0, 100, 100).value) shouldBe 9818487
  }

  private def distance(sourceLatitude: Double, sourceLongitude: Double, targetLatitude: Double,
    targetLongitude: Double): Distance = {
    Location(None, None, Latitude(sourceLatitude), Longitude(sourceLongitude)).distance(
      Location(None, None, Latitude(targetLatitude), Longitude(targetLongitude)),
      DistanceUnit.Meters)
  }

}