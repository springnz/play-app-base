package ylabs.play.common.mocks

import java.io.InputStream
import java.net.URI

import ylabs.play.common.models.FileUpload.FileDescription
import ylabs.play.common.services.FileUploadService

import scala.concurrent.{ExecutionContext, Future}

class FileUploadServiceMock extends FileUploadService {
  var enable = true
  override def upload(image: InputStream, description: Option[FileDescription])(implicit ec: ExecutionContext) = {
    if (enable) Future {
      new URI("https://" + bucketName)
    }
    else Future {
      throw new UnsupportedOperationException("Problem")
    }
  }
}