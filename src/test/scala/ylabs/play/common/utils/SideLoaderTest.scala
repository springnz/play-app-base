package ylabs.play.common.utils

import org.scalatest.{Matchers, WordSpec}
import ylabs.play.common.models.Helpers.Id

import scala.concurrent.Await

class SideLoaderTest extends WordSpec with Matchers {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  import SideLoadTestHelpers._

  "test basic side load" in {
    val sideLoadProcessor = new TestSideLoadProcessor()
    val res = SideLoadTestHelpers.resultMap.find(_.id == Id[Result]("t1")).get
    val result = Await.result(sideLoadProcessor.load(res.toInfo, Some(TestSideLoadOptions(anotherResults = true))), 1.seconds)
    result.anotherResults.keys shouldBe Set(Id[AnotherResult]("a1"))
    result.testResults.keys shouldBe Set(Id[Result]("t1"))
  }
}
