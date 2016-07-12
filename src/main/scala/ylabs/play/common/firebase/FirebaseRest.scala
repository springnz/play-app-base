package ylabs.play.common.firebase

import javax.inject.{Inject, Singleton}

import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.utils.Configuration
import ylabs.play.common.utils.FailureType.FirebaseError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class FirebaseRest @Inject() (ws: WSClient, conf: Configuration) extends LazyLogging {

  lazy val firebaseUrl = conf.config.getString("firebase.url")

  def create[T](path: String, data: JsValue): Future[Id[T]] = {
    val url = firebaseUrl.concat(path)

    ws.url(url)
      .post(data)
      .map { response =>
        response.status match {
          case 200 ⇒ (response.json \ "name").as[Id[T]]
          case _ ⇒ {
            logError(url, Some(data), Some(response))
            throw FirebaseError
          }
        }
      } recover {
        case FirebaseError ⇒ throw FirebaseError
        case e ⇒
          logError(url, Some(data), None, Some(e))
          throw FirebaseError
      }
  }

  def update(path: String, data: JsValue): Future[Unit] = {
    val url = firebaseUrl.concat(path)

    ws.url(url)
      .patch(data)
      .map { response =>
        response.status match {
          case 200 ⇒
          case _ ⇒ {
            logError(url, Some(data), Some(response))
            throw FirebaseError
          }
        }
      } recover {
      case FirebaseError ⇒ throw FirebaseError
      case e ⇒
        logError(url, Some(data), None, Some(e))
        throw FirebaseError
    }
  }

  def delete(path: String): Future[Unit] = {
    val url = firebaseUrl.concat(path)

    ws.url(url)
      .delete()
      .map { response =>
        response.status match {
          case 200 ⇒
          case _ ⇒ {
            logError(url, None, Some(response))
            throw FirebaseError
          }
        }
      } recover {
      case FirebaseError ⇒ throw FirebaseError
      case e ⇒
        logError(url, None, None, Some(e))
        throw FirebaseError
    }
  }

  def logError(url: String, data: Option[JsValue] = None, response: Option[WSResponse] = None, error: Option[Throwable] = None): Unit = {
    response.map { resp ⇒
      val err = (resp.json \ "error").as[String]
      logger.error(s"response: $err (${resp.status})")
    }

    logger.error(s"url: $url")
    logger.error(s"data: $data")

    error.map(e ⇒ logger.error(e.getMessage, e))
  }
}
