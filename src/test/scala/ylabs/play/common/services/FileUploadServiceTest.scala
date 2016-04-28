package ylabs.play.common.services

import java.io.{File, FileInputStream}
import java.nio.file.Paths
import java.time.Instant
import java.util.Date

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import play.api.libs.ws.WSClient
import ylabs.play.common.models.FileUpload.FileDescription
import ylabs.play.common.test.{OneAppPerTestWithOverrides, MyPlaySpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class FileUploadServiceTest extends MyPlaySpec with OneAppPerTestWithOverrides with ScalaFutures with BeforeAndAfter with MockitoSugar {
  "should upload to S3" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    val uri = Await.result(new FileUploadService().upload(
      new FileInputStream(new File(Paths.get("src/test/resources/healthcheck-upload.png").toAbsolutePath.toString)),
      Some(FileDescription("test shuttle"))), 10.seconds)
    lazy val config = ConfigFactory.load()
    lazy val bucketName = config.getString("aws.s3.bucket")
    uri.toString should startWith("https://" + bucketName)

    val expiresRegex = "Expires=([0-9]+)".r
    val expireMatch = expiresRegex.findFirstMatchIn(uri.toString)
    val expireTime = Date.from(Instant.ofEpochSecond(expireMatch.map(_.group(1)).get.toLong))

    val testDate = Date.from(new Date().toInstant.minusSeconds(1000).plusMillis(FileUploadService.binExpirationTime))
    expireTime.after(testDate) shouldBe true


    val wsClient = app.injector.instanceOf[WSClient]
    val res = Await.result(wsClient.url(uri.toString).get(), 10.seconds)
    res.status shouldBe 200
  }
}
