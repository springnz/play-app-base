package ylabs.play.common.firebase.rest

import javax.inject.{Inject, Singleton}

import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.User._
import ylabs.play.common.utils.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UserFirebaseRest @Inject() (ws: WSClient, conf: Configuration) {

  lazy val fbUrl = conf.config.getString("firebase.url")

  def create(phone: Phone, name: Name, status: Status): Future[Id[User]] = {
    val fbData = UserFirebaseCreate(phone, name, status)

    ws.url(s"$fbUrl/users.json")
      .post(Json.toJson(fbData))
      .map {
        resp => (resp.json \ "name").as[Id[User]]
      }
  }

  def update(userId: Id[User], name: Option[Name] = None, status: Option[Status] = None) = {
    val fbData = UserFirebaseUpdate(name, status)

    ws.url(s"$fbUrl/users/${userId.value}.json")
      .patch(Json.toJson(fbData))
  }
}
