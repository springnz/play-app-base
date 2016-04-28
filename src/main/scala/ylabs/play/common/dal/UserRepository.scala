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

  def createFromVertex(vertex: Future[Option[Vertex]],
                       phone: Option[Phone],
                       email: Option[Email],
                       name: Name,
                       status: Status,
                       endpoint: Option[DeviceEndpoint]): Future[User] = vertex map {
    case Some(userVertex) ⇒
      log.debug(s"user already exists as $userVertex, no need to create")
      if (status == Status(Registered)) {
        userVertex.setProperty(Properties.Name, name.value)
        userVertex.setProperty(Properties.Status, Registered)
        userVertex.setProperty(Properties.DeviceEndpoint, endpoint.getOrElse(DeviceEndpoint("")).value)
      }
      apply(userVertex)
    case None ⇒
      log.debug(s"creating user for ${if (phone.nonEmpty) phone.get.value else name}")
      val id = Id[User]()
      val user = User(id, name, status, phone, email, endpoint)
      graph + user
      user
  }

  def createFromPhone(phone: Phone, name: Name, status: Status, endpoint: Option[DeviceEndpoint] = None): Future[User] = {
    val future = createFromVertex(getVertexFromPhone(phone), Some(phone), None, name, status, endpoint)
    future.onComplete {
      case Success(vertex) ⇒ log.info(s"registration successful: ${phone.value} -> $vertex")
      case Failure(t)      ⇒ log.error(s"cannot create user for ${phone.value}", t)
    }
    future
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