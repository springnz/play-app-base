package ylabs.play.common.dal


import java.util.Date
import javax.inject.{Inject, Singleton}

import gremlin.scala.Vertex
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.Sms._
import ylabs.play.common.models.StoredSms._
import gremlin.scala._
import ylabs.play.common.models.User.Phone
import scala.concurrent.Future

@Singleton
class SmsRepository @Inject() (graphDB: GraphDB)  {
  implicit val context = graphDB.context
  lazy val graph = graphDB.graph

  @label(Label)
  case class StoredCreate(id: Id[StoredSms], from: From, to: Phone, body: Text, status: Status, errorCode: ErrorCode, timesTried: Int, created: Date, lastTried: Date )

  def create(id: Id[StoredSms], sms: SmsStatusChanged): Future[StoredSms] = {
    getVertex(id) map {
      case Some(v) =>
        v.setProperty(Properties.TimesTried, v.value2(Properties.TimesTried))
        v.setProperty(Properties.LastTried, new Date())
        apply(v)
      case None =>
        val vertex = graph + StoredCreate(id, sms.from, sms.to, sms.body, sms.status, sms.errorCode, 1, new Date(), new Date())
        apply(vertex)
    }
  }

  def getVertex(id: Id[StoredSms]): Future[Option[Vertex]] = Future {
    graph.V.hasLabel(Label).has(Properties.Id, id.value).headOption
  }

  def get(id: Id[StoredSms]): Future[Option[StoredSms]] = getVertex(id).map(_.map(apply))
}