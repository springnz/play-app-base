package ylabs.play.common.dal

import java.util.concurrent.Executors
import javax.inject.Singleton

import com.typesafe.config.ConfigFactory
import gremlin.scala.{ScalaGraph, _}
import org.apache.tinkerpop.gremlin.orientdb.{OrientGraph, OrientGraphFactory}

import scala.concurrent.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@Singleton
class GraphDB {
  lazy val config = ConfigFactory.load().getConfig("orientdb")
  lazy val url = config.getString("url")
  lazy val user = config.getString("user")
  lazy val pass = config.getString("pass")

  /**
    * Single threaded executor used here due to OrientDB limitations. Need to be replaced,
    * as soon as connection pool will be integrated.
    *
    *
    * 'The rule is that one database instance can be used by a thread, because
    * database instances are not thread safe.'
    *
    * @see https://github.com/mpollmeier/orientdb-gremlin/issues/16
    */

  implicit val context = asExecutionContext(Executors.newSingleThreadExecutor())

  val graph: ScalaGraph[OrientGraph] = Await.result(Future(new OrientGraphFactory(url, user, pass).getNoTx.asScala), 5.seconds)
}
