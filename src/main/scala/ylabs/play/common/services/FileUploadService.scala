package ylabs.play.common.services

import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.util.{Date, UUID}

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.typesafe.config.ConfigFactory
import ylabs.play.common.models.FileUpload.FileDescription

import scala.concurrent.{ExecutionContext, Future}

object FileUploadService {
  val expirationYears:Long = 20
  val binExpirationTime: Long = expirationYears*365L*24L*60L*60L*1000L
}

class FileUploadService {
  lazy val s3 = new AmazonS3Client
  lazy val config = ConfigFactory.load()
  lazy val bucketName = config.getString("aws.s3.bucket")
  def upload(file: InputStream, description: Option[FileDescription])(implicit ec: ExecutionContext): Future[URI] = {
    Future {
      if (!s3.doesBucketExist(bucketName)) s3.createBucket(bucketName)

      val metadata = new ObjectMetadata
      metadata.addUserMetadata("Description", description.getOrElse(FileDescription("None")).value)
      val id = UUID.randomUUID().toString
      val putObj = s3.putObject(bucketName, id, file, metadata)
      val expireDate = Date.from(Instant.ofEpochMilli(new Date().getTime + FileUploadService.binExpirationTime))
      s3.generatePresignedUrl(bucketName, id, expireDate).toURI
    }
  }
}
