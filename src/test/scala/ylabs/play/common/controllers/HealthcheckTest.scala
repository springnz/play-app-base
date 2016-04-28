package ylabs.play.common.controllers

import java.io.InputStream

import gremlin.scala.ScalaGraph
import org.apache.tinkerpop.gremlin.orientdb.{OrientGraph, OrientGraphFactory}
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import ylabs.play.common.dal.GraphDB
import ylabs.play.common.models.FileUpload.FileDescription
import ylabs.play.common.services.FileUploadService
import ylabs.play.common.test.{ErrorEjabberdTest, MyPlaySpec, OneAppPerTestWithOverrides}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class HealthcheckOkTest extends MyPlaySpec with OneAppPerTestWithOverrides {
  "returns 200 OK if all systems are up" in {
    val request = FakeRequest(GET, "/healthcheck")
    val response = route(app, request).get
    status(response) shouldBe OK

    val errors = Json.fromJson[Seq[String]](contentAsJson(response)).get
    errors shouldBe Nil
  }
}

class HealthcheckEjabberdErrorTest extends MyPlaySpec with OneAppPerTestWithOverrides with ErrorEjabberdTest {
  "returns 503 SERVICE_UNAVAILABLE if ejabberd is down" in {
    val request = FakeRequest(GET, "/healthcheck")
    val response = route(app, request).get
    status(response) shouldBe SERVICE_UNAVAILABLE
    HealthcheckTest.assertError("Ejabberd")(response)
  }
}

class HealthcheckOrientErrorTest extends MyPlaySpec with OneAppPerTestWithOverrides {
  override def overrideModules = Seq(bind[GraphDB].toInstance(
    new GraphDB {
      override val graph: ScalaGraph[OrientGraph] = {
        new ScalaGraph(new OrientGraphFactory("memory:errortest", "user", "pass").getNoTx) {
          override def V = throw new Exception("mocking out orient error") with NoStackTrace
        }
      }
    }))

  "returns 503 SERVICE_UNAVAILABLE if orientdb is down" in {
    val request = FakeRequest(GET, "/healthcheck")
    val response = route(app, request).get
    status(response) shouldBe SERVICE_UNAVAILABLE
    HealthcheckTest.assertError("Orientdb")(response)
  }
}

class HealthcheckS3ErrorTest extends MyPlaySpec with OneAppPerTestWithOverrides {
  override def overrideModules = Seq(bind[FileUploadService].toInstance(
    new FileUploadService {
      override def upload(i: InputStream, d: Option[FileDescription])(implicit ec: ExecutionContext) =
        Future.failed(new scala.Exception("mocking out s3 test") with NoStackTrace)
    }))

  "returns 503 SERVICE_UNAVAILABLE if S3 is down" in {
    val request = FakeRequest(GET, "/healthcheck")
    val response = route(app, request).get
    status(response) shouldBe SERVICE_UNAVAILABLE
    HealthcheckTest.assertError("S3")(response)
  }
}

object HealthcheckTest extends MyPlaySpec {
  def assertError(wanted: String)(response: Future[Result]) {
    val errors = Json.fromJson[Seq[String]](contentAsJson(response)).get
    errors should contain(wanted)
  }
}