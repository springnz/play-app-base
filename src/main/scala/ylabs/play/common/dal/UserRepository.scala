package ylabs.play.common.dal

import javax.inject.{ Inject, Singleton }

import gremlin.scala._
import springnz.util.Logging
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.User._
import ylabs.play.common.utils.FailureType

import scala.concurrent.Future
import scala.util.{ Failure, Success }

@Singleton
class UserRepository @Inject() (graphDB: GraphDB) extends Logging {

  implicit val context = graphDB.context

  lazy val graph = graphDB.graph

  def list: Future[List[User]] = Future {
    graph.V.hasLabel(Label).map(apply).toList()
  }

  @label("user")
  case class CreateUser(id: Id[User], status: Status, phone: Phone, name: Name, deviceActivated: Boolean = false, isTester: Boolean = false)

  def createFromPhone(phone: Phone, status: Status, name: Name, deviceId: Option[DeviceId], idFirebase: Option[Id[User]] = None): Future[User] = {
    getVertexFromPhone(phone) map {
      case Some(vertex) => apply(vertex)
      case None =>
        val id = idFirebase.getOrElse(Id[User]())
        val create = CreateUser(id, status, phone, name)
        val vertex = graph + create
        deviceId.map(i ⇒ vertex.setProperty(Properties.DeviceId, i.value))
        User(id, name, status, deviceActivated = false, isTester = false, Some(phone), None, deviceId, None, None)
    }
  }

  def setPushEndpoint(id: Id[User], endpoint: DeviceEndpoint): Future[User] = {
    getVertexOption(id) map {
      case None ⇒ throw FailureType.RecordNotFound
      case Some(vertex) ⇒
        vertex.setProperty(Properties.DeviceEndpoint, endpoint.value)
        apply(vertex)
    }
  }

  def loginWithPhone(phone: Phone, name: Name, status: Status, deviceId: DeviceId): Future[Either[FailureType, User]] = {
    getVertexFromPhone(phone) map {
      case None ⇒ Left(FailureType.RecordNotFound)
      case Some(vertex) ⇒
        vertex.value2(Properties.IsTester) match {
          case true => Right(apply(vertex))

          case _ =>
            deviceId match {
              /*case _ if vertex.valueOption(Properties.DeviceId).isEmpty      ⇒ Left(FailureType.DeviceIdDoesNotMatch)
              case _ if vertex.value2(Properties.DeviceId) != deviceId.value ⇒ Left(FailureType.DeviceIdDoesNotMatch)
              case _ if !vertex.value2(Properties.DeviceActivated)           ⇒ Left(FailureType.DeviceNotActivated)*/
              case _ ⇒
                if (status == Status(Registered)) {
                  vertex.setProperty(Properties.Name, name.value)
                  vertex.setProperty(Properties.Status, Registered)
                }
                Right(apply(vertex))
            }
        }
    }
  }

  def clearDevice(id: Id[User]): Future[User] = {
    getVertexOption(id) map {
      case None ⇒ throw FailureType.RecordNotFound
      case Some(vertex) ⇒
        vertex.setProperty(Properties.DeviceActivated, false)
        apply(vertex)
    }
  }

  def registerDevice(phone: Phone, code: Code, deviceId: DeviceId): Future[Either[FailureType, User]] = {
    getVertexFromPhone(phone) map {
      case None ⇒ Left(FailureType.RecordNotFound)
      case Some(vertex) if vertex.value2(Properties.DeviceId) != deviceId.value ⇒
        Left(FailureType.DeviceIdDoesNotMatch)
      case Some(vertex) if vertex.valueOption(Properties.Code).isEmpty  ⇒ Left(FailureType.DeviceCodeMissing)
      case Some(vertex) if vertex.value2(Properties.Code) != code.value ⇒ Left(FailureType.DeviceCodeDoesNotMatch)

      case Some(vertex) ⇒
        vertex.removeProperties(Properties.Code)
        vertex.setProperty(Properties.DeviceActivated, true)
        vertex.setProperty(Properties.Status, Registered)
        Right(apply(vertex))
    }
  }

  def setDeviceCode(phone: Phone, code: Code, deviceId: DeviceId): Future[Either[FailureType, User]] = {
    getVertexFromPhone(phone) map {
      case None ⇒ Left(FailureType.RecordNotFound)
      case Some(vertex) ⇒
        vertex.setProperty(Properties.DeviceId, deviceId.value)
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

  def update(id: Id[User], phone: Option[Phone], email: Option[Email], name: Option[Name], status: Option[Status]): Future[Either[FailureType, User]] =
    getVertexOption(id).map {
      case Some(vertex) ⇒
        // only update the properties that are defined
        phone.map { phone ⇒ vertex.setProperty(Properties.Phone, phone.value) }
        email.map { email ⇒ vertex.setProperty(Properties.Email, email.value) }
        name.map { name ⇒ vertex.setProperty(Properties.Name, name.value) }
        status.map { status ⇒ vertex.setProperty(Properties.Status, status.value) }
        Right(apply(vertex))
      case None ⇒ Left(FailureType.RecordNotFound)
    }

  def setTesterStatus(id: Id[User], isTester: Boolean) = {
    getVertexOption(id) map {
      case Some(vertex) ⇒ vertex.setProperty(Properties.IsTester, isTester)
      case _            ⇒ Left(FailureType.RecordNotFound)
    }
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