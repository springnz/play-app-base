package ylabs.play.common.dal

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OClass.INDEX_TYPE
import com.orientechnologies.orient.core.metadata.schema.{OImmutableClass, OType}
import com.typesafe.config.ConfigFactory
import springnz.orientdb.ODBScala
import springnz.orientdb.migration.{Migration, Migrator, ODBMigrations}
import springnz.orientdb.pool.ODBConnectionPool
import springnz.orientdb.session.ODBSession
import springnz.util.Logging
import ylabs.play.common.models.{StoredSms, Location, User}

import scala.util.{Failure, Success}

trait Migrations extends ODBMigrations with ODBScala with Logging {
  lazy val conf = ConfigFactory.load
  implicit lazy val pool = ODBConnectionPool.fromConfig(conf.getConfig("orientdb"))

  def migrations: Seq[Migration] = Seq()

  def getOrCreateVertexClass(name: String)(implicit db: ODatabaseDocumentTx) =
    findClass(OImmutableClass.VERTEX_CLASS_NAME + "_" +name) match {
      case null => createVertexClass(name)
      case c => c
    }

  def getOrCreateEdgeClass(name: String)(implicit db: ODatabaseDocumentTx) =
    findClass(OImmutableClass.EDGE_CLASS_NAME + "_" +name) match {
      case null => createEdgeClass(name)
      case c => c
    }


  // called on construction, just like in play's own ApplicationEvolutions
  def run(): Unit = {
    log.info("applying db migrations")

    Migrator.runMigration(migrations) match {
      case Success(_) ⇒ log.info(s"successfully applied db migrations")
      case Failure(t) ⇒
        log.error("error while applying db migrations", t)
        throw t
    }
  }

  def timezone: ODBSession[Unit] =
    ODBSession { implicit db ⇒
      sqlCommand("ALTER DATABASE TIMEZONE UTC").execute()
      () //the above orient command returns null. silly java! avoid NPEs by explicitly returning Unit
    }

  def userClass: ODBSession[Unit] =
    ODBSession { implicit db ⇒
      val userClass = getOrCreateVertexClass(User.Label)

      userClass.createProperty(User.Properties.Id.value, OType.STRING)
        .setMandatory(true)
        .setReadonly(true)
        .setNotNull(true)

      userClass.createIndex(
        "user.id",
        INDEX_TYPE.UNIQUE_HASH_INDEX,
        User.Properties.Id.value)

      userClass.createProperty(User.Properties.Phone.value, OType.STRING)
        .setMandatory(true)
        .setNotNull(true)
        .setReadonly(true)

      userClass.createIndex(
        "user.phone",
        INDEX_TYPE.UNIQUE_HASH_INDEX,
        User.Properties.Phone.value)
    }

  def locationClass: ODBSession[Unit] =
    ODBSession { implicit db ⇒
      val locationClass = getOrCreateVertexClass(Location.Label)

      /**
        * ID property & index
        */
      locationClass.createProperty(Location.Properties.Id.value, OType.STRING)
        .setMandatory(true)
        .setReadonly(true)
        .setNotNull(true)

      locationClass.createIndex(
        "location.id",
        INDEX_TYPE.UNIQUE_HASH_INDEX,
        Location.Properties.Id.value)

      /**
        * Timestamp property & index
        */
      locationClass.createProperty(Location.Properties.Timestamp.value, OType.DATETIME)
        .setMandatory(true)
        .setReadonly(true)
        .setNotNull(true)

      locationClass.createIndex(
        "location.timestamp",
        INDEX_TYPE.NOTUNIQUE_HASH_INDEX,
        Location.Properties.Timestamp.value)
    }

  def smsStorageClass: ODBSession[Unit] =
    ODBSession { implicit db ⇒
      val smsClass = getOrCreateVertexClass(StoredSms.Label)

      /**
       * ID property & index
       */
      smsClass.createProperty(StoredSms.Properties.Id.value, OType.STRING)
        .setMandatory(true)
        .setReadonly(true)
        .setNotNull(true)

      smsClass.createIndex(
        "storedsms.id",
        INDEX_TYPE.UNIQUE_HASH_INDEX,
        StoredSms.Properties.Id.value)

      /**
       * Timestamp property & index
       */
      smsClass.createProperty(StoredSms.Properties.LastTried.value, OType.DATETIME)
        .setMandatory(true)
        .setReadonly(true)
        .setNotNull(true)

      smsClass.createIndex(
        "storedsms.lastTried",
        INDEX_TYPE.NOTUNIQUE_HASH_INDEX,
        StoredSms.Properties.LastTried.value)
    }

  run()
}
