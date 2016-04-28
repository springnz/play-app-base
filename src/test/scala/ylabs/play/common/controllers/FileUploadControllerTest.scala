package ylabs.play.common.controllers

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.Paths
import java.util.Base64

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.inject._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import ylabs.play.common.mocks.FileUploadServiceMock
import ylabs.play.common.models.FileUpload.{Base64String, FileDescription, FileUploadRequest, FileUploadResponse}
import ylabs.play.common.models.User
import ylabs.play.common.models.User.{Phone, RegistrationRequest}
import ylabs.play.common.services.FileUploadService
import ylabs.play.common.test.TestTools._
import ylabs.play.common.test.{MyPlaySpec, OneAppPerTestWithOverrides, UserTools}

class FileUploadControllerTest extends MyPlaySpec with OneAppPerTestWithOverrides with ScalaFutures with BeforeAndAfter with MockitoSugar {

  override def overrideModules = Seq(bind[FileUploadService].toInstance(new FileUploadServiceMock))
  "should upload" in new Fixture {
    val data = getData
    val request = FakeRequest(POST, "/cloud")
      .withJsonBody(Json.toJson(FileUploadRequest(Some(FileDescription("test file")), new Base64String(data))))

    val response = route(app, request).get

    withAuthVariants(request, jwt) { response ⇒
      status(response) shouldBe OK
      val location = Json.fromJson[FileUploadResponse](contentAsJson(response)).get
      location.url.value should startWith("https://" + bucketName)
    }
  }

  "should report when s3 isn't working" in new Fixture {
    app.injector.instanceOf[FileUploadService].asInstanceOf[FileUploadServiceMock].enable = false
    val data = getData
    val request = FakeRequest(POST, "/cloud")
      .withJsonBody(Json.toJson(FileUploadRequest(Some(FileDescription("test file")), new Base64String(data))))
      .withAuth(jwt)

    intercept[UnsupportedOperationException] {
      val response = route(app, request).get
      status(response)
    }
  }

  "should fail if not base64" when {
    "missing comma" in new Fixture {
      val data = getData.replace(",", "")
      val request = FakeRequest(POST, "/cloud")
        .withJsonBody(Json.toJson(FileUploadRequest(Some(FileDescription("test file")), new Base64String(data))))
        .withAuth(jwt)
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
    }

    "missing base64 signifier" in new Fixture {
      val data = getData.replace("base64", "")
      val request = FakeRequest(POST, "/cloud")
        .withJsonBody(Json.toJson(FileUploadRequest(Some(FileDescription("test file")), new Base64String(data))))
        .withAuth(jwt)
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
    }

    "missing data: prefix" in new Fixture {
      val data = getData.replace("data:", "")
      val request = FakeRequest(POST, "/cloud")
        .withJsonBody(Json.toJson(FileUploadRequest(Some(FileDescription("test file")), new Base64String(data))))
        .withAuth(jwt)
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
    }

    "bad encoding" in new Fixture {
      val data = getData.replace('A', '∫')
      val request = FakeRequest(POST, "/cloud")
        .withJsonBody(Json.toJson(FileUploadRequest(Some(FileDescription("test file")), new Base64String(data))))
        .withAuth(jwt)
      val response = route(app, request).get
      status(response) shouldBe BAD_REQUEST
    }
  }

  trait Fixture {
    lazy val config = ConfigFactory.load()
    lazy val bucketName = config.getString("aws.s3.bucket")
    val phoneNumber = "+64212345678"
    val registration = RegistrationRequest(Phone(phoneNumber), None, User.Name("test name"))
    val jwt = UserTools.registerUser(registration).jwt
    val prefix = "data:image/png;base64,"

    def getData = {
      val file = new File(Paths.get("src/test/resources/healthcheck-upload.png").toAbsolutePath.toString)
      val reader = new BufferedInputStream(new FileInputStream(file))
      val buffer = new Array[Byte](file.length.asInstanceOf[Int])
      reader.read(buffer)
      reader.close()
      prefix + new String(Base64.getEncoder.encode(buffer))
    }
  }
}