package ylabs.play.common.dal

import javax.inject.{Inject, Singleton}

import gremlin.scala._
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.structure.Vertex
import springnz.util.Logging
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.Location._
import ylabs.play.common.models.User
import ylabs.play.common.utils.FailureType

import scala.concurrent.Future
import scala.language.implicitConversions

object LocationRepository {
  implicit def fromVertexToLocation(node: Vertex): Location = ylabs.play.common.models.Location.apply(node)

  implicit def fromVertexOptionToLocation(node: Option[Vertex]): Option[Location] = node.map(fromVertexToLocation(_))

  implicit def fromVertexListToLocation(nodes: List[Vertex]): List[Location] = nodes map fromVertexToLocation
}

@Singleton
class LocationRepository @Inject() (graphDB: GraphDB, userRepository: UserRepository) extends Logging {
  import LocationRepository._
  implicit val context = graphDB.context
  lazy val graph = graphDB.graph

  def get(id: Id[Location]): Future[Option[Location]] =
    Future {
      graph.V.hasLabel(Label).has(Properties.Id, id.value).headOption
    }

  def create(user: Id[User.User], location: Location): Future[Id[Location]] =
    userRepository.getVertexOption(user) flatMap {
      case Some(userVertex) ⇒
        val id = Id[Location]()

        val locationVertex = graph + (
          Label,
          Properties.Id -> id.value,
          Properties.Timestamp -> location.timestamp,
          Properties.LocationLatitude -> location.latitude.value,
          Properties.LocationLongitude -> location.longitude.value)

        locationVertex --- BelongsTo --> userVertex
        Future.successful(id)
      case None ⇒ Future.failed(FailureType.RecordNotFound)
    }

  def listNearby(distanceThreshold: Distance, target: Location): Future[Map[User.User, Distance]] = {
    Future {
      def distanceFrom(location: Location): Option[Distance] =
        location.distance(target, DistanceUnit.Meters) match {
          case distance if distance.value < DistanceUnit.Meters.convert(distanceThreshold).value ⇒
            Some(distance)
          case _ ⇒ None
        }

      val userDistance = for {
        user ← graph.V.hasLabel(User.Label)
        location ← lastLocation(user)
        distance ← location.start.map(distanceFrom(_)).collect { case Some(distance) ⇒ distance }
      } yield (User(user), distance)

      userDistance.toList.toMap
    }
  }

  def lastUserLocation(userId: Id[User.User]): Future[Option[Location]] =
    Future {
      graph.V.hasLabel(User.Label)
        .has(User.Properties.Id, userId.value)
        .in(BelongsTo)
        .orderBy(Properties.Timestamp.value, Order.decr)
        .headOption()
    }

  def listByUser(userId: Id[User.User]): Future[Seq[Location]] =
    Future {
      graph.V.hasLabel(User.Label)
        .has(User.Properties.Id, userId.value)
        .in(BelongsTo)
        .toList()
    }

  def lastLocation(user: Vertex) =
    user.in(BelongsTo)
      .orderBy(Properties.Timestamp.value, Order.decr)
      .limit(1)

}

