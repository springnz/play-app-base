package ylabs.play.common.firebase.rest

import javax.inject.{Inject, Singleton}

import play.api.libs.json.Json
import ylabs.play.common.firebase.FirebaseRest
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.User._

import scala.concurrent.Future

@Singleton
class UserFirebaseRest @Inject() (fb: FirebaseRest) {

  def create(phone: Phone, name: Name, status: Status): Future[Id[User]] = {

    val path = "/users.json"
    val data = UserFirebaseCreate(phone, name, status)

    fb.create[User](path, Json.toJson(data))
  }

  def update(userId: Id[User], name: Option[Name] = None, status: Option[Status] = None) = {

    val path = s"/users/${userId.value}.json"
    val data = UserFirebaseUpdate(name, status)

    fb.update(path, Json.toJson(data))
  }
}
