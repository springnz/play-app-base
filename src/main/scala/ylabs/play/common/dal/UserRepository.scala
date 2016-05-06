package ylabs.play.common.dal

import javax.inject.{Inject, Singleton}

import gremlin.scala._
import springnz.util.Logging
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.User._
import ylabs.play.common.utils.FailureType

import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class UserRepository @Inject() (graphDB: GraphDB) extends Logging {

  implicit val context = graphDB.context

  lazy val graph = graphDB.graph

  def list: Future[List[User]] = Future {
    graph.V.hasLabel(Label).map(apply).toList()
  }




  @label("user")
  case class CreateUser(id: Id[User], status: Status, phone: Phone, name: Name, deviceId: Option[DeviceId], deviceActivated: Boolean = false)

  def createFromPhone(phone: Phone, status: Status, name: Name, deviceId: Option[DeviceId]): Future[User] = {
    Future {
      val id = Id[User]()
      val create = CreateUser(id, status, phone, name, deviceId)
      val vertex = graph + create
      User(id, name, status, deviceActivated = false, Some(phone), None, deviceId, None, None)
    }
  }

  def setPushEndpoint(id: Id[User], endpoint: DeviceEndpoint): Future[User] = {
    getVertexOption(id) map {
      case None => throw FailureType.RecordNotFound
      case Some(vertex) =>
        vertex.setProperty(Properties.DeviceEndpoint, endpoint.value)
        apply(vertex)
    }
  }

  def loginWithPhone(phone: Phone, name: Name, status: Status, deviceId: DeviceId): Future[Either[FailureType, User]] = {
    getVertexFromPhone(phone) map {
      case None => Left(FailureType.RecordNotFound)
      case Some(vertex) =>
        deviceId match {
          case _ if vertex.value2(Properties.DeviceId) != deviceId.value => Left(FailureType.DeviceIdDoesNotMatch)
          case _ if !vertex.value2(Properties.DeviceActivated) => Left(FailureType.DeviceNotActivated)
          case _ =>
            if (status == Status(Registered)) {
              vertex.setProperty(Properties.Name, name.value)
              vertex.setProperty(Properties.Status, Registered)
            }
            Right(apply(vertex))
        }
      }
  }

  def registerDevice(phone: Phone, code: Code, deviceId: DeviceId): Future[Either[FailureType, User]] = {
    getVertexFromPhone(phone) map {
      case None => Left(FailureType.RecordNotFound)
      case Some(vertex) if vertex.value2(Properties.DeviceId) != deviceId.value => Left(FailureType.DeviceIdDoesNotMatch)
      case Some(vertex) if vertex.valueOption(Properties.Code).isEmpty => Left(FailureType.DeviceCodeMissing)
      case Some(vertex) if vertex.value2(Properties.Code) != code.value => Left(FailureType.DeviceCodeDoesNotMatch)

      case Some(vertex) =>
        vertex.removeProperties(Properties.Code)
        vertex.setProperty(Properties.DeviceActivated, true)
        Right(apply(vertex))
    }
  }

  def setDeviceCode(phone: Phone, code: Code, deviceId: DeviceId) : Future[Either[FailureType, User]] = {
    getVertexFromPhone(phone) map {
      case None =>  Left(FailureType.RecordNotFound)
      case Some(vertex) =>
        vertex.setProperty(Properties.DeviceEndpoint, deviceId.value)
        vertex.setProperty(Properties.Code, code.value)
        vertex.setProperty(Properties.DeviceActivated, false)
        Right(apply(vertex))
    }
  }



  //TODO once we have user management sorted
//  def createFromEmail(email: Email, name: Name, status: Status, endpoint: Option[DeviceEndpoint] = None): Future[User] = {
//    val future = createFromVertex(getVertexFromEmail(email), None, Some(email), name, status, endpoint)
//    future.onComplete {
//      case Success(vertex) ⇒ log.info(s"registration successful: ${email.value} -> $vertex")
//      case Failure(t)      ⇒ log.error(s"cannot create user for ${email.value}", t)
//    }
//    future
//  }

  def update(id: Id[User], update: UserUpdateRequest): Future[Either[FailureType, User]] =
    getVertexOption(id).map {
      case Some(vertex) ⇒
        // only update the properties that are defined
        update.phone.map { phone ⇒ vertex.property(Properties.Phone.value, phone.value) }
        update.email.map { email ⇒ vertex.property(Properties.Email.value, email.value) }
        update.name.map { name ⇒ vertex.property(Properties.Name.value, name.value) }
        Right(apply(vertex))
      case None ⇒ Left(FailureType.RecordNotFound)
    }

  def get(id: Id[User]): Future[Option[User]] =
    getVertexOption(id) map (_.map(apply))

  def getFromEmail(email: Email): Future[Option[User]] =
    getVertexFromEmail(email) map (_.map(apply))

  def getFromPhone(phone: Phone): Future[Option[User]] =
    getVertexFromPhone(phone) map (_.map(apply))

  def getVertexOption(id: Id[User]): Future[Option[Vertex]] =
    Future {
      graph.V
        .hasLabel(Label)
        .has(Properties.Id, id.value)
        .headOption()
    }

  def getVertexFromPhone(phone: Phone): Future[Option[Vertex]] =
    Future {
      graph.V
        .hasLabel(Label)
        .has(Properties.Phone, phone.value)
        .headOption()
    }

  def getVertexFromEmail(email: Email): Future[Option[Vertex]] =
    Future {
      graph.V
        .hasLabel(Label)
        .has(Properties.Email, email.value)
        .headOption()
    }
}