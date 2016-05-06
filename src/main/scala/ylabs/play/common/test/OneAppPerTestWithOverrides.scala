package ylabs.play.common.test

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.mockito.Matchers._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Suite, SuiteMixin, TestData}
import play.api.inject._
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers
import play.api.{Application, Mode}
import ylabs.play.common.models.PushNotification.{Platform, Token}
import ylabs.play.common.models.User.{Code, DeviceEndpoint}
import ylabs.play.common.services.PushNotificationService
import ylabs.play.common.test.mocks.SmackErrorMock
import ylabs.play.common.utils.{CodeGenerator, SmackUtils}

import scala.concurrent.{ExecutionContext, Future}

trait OneAppPerTestWithOverrides extends SuiteMixin with GuiceOverrides { this: Suite ⇒
  implicit val actorSystem = ActorSystem("testActorSystem", ConfigFactory.load())

  def additionalConfiguration: Map[String, _ <: Any] = Map.empty

  def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder()
      .in(Mode.Test)
      .configure(additionalConfiguration)
      .overrides(ejabberdModule)
      .overrides(pushNotificationsModule)
      .overrides(codeModule)
      .overrides(overrideModules: _*)
      .build

  private var appPerTest: Application = _
  implicit final def app: Application = synchronized { appPerTest }

  abstract override def withFixture(test: NoArgTest) = {
    //(new Migrations).run()
    synchronized { appPerTest = newAppForTest(test) }
    Helpers.running(app) {
      super.withFixture(test)
    }
  }

  //TODO: figure out the correct way how to run migrations per test
  /*class Migrations extends ylabs.play.common.dal.Migrations {
    override lazy val migrations = Seq(
      Migration(0, timezone),
      Migration(1, userClass),
      Migration(2, locationClass)
    )
  }*/
}

trait GuiceOverrides extends MockitoSugar {
  def overrideModules: Seq[GuiceableModule] = Nil
  implicit val smackUtils: SmackUtils = mock[SmackUtils]

  implicit val pushNotificationService = {
    val ret = mock[PushNotificationService]

    when(ret.register(
      any[String].asInstanceOf[Platform],
      any[String].asInstanceOf[Token],
      any[Option[String]].asInstanceOf[Option[DeviceEndpoint]])
      (any[ExecutionContext]))
      .thenReturn(Future.successful(Some(DeviceEndpoint(""))))
    ret
  }

  implicit val generatorMock = {
    val ret = mock[CodeGenerator]

    Mockito.doReturn("0000").when(ret).createCode()
    ret
  }

  def ejabberdModule: GuiceableModule = bind[SmackUtils].toInstance(smackUtils)
  def pushNotificationsModule: GuiceableModule = bind[PushNotificationService].toInstance(pushNotificationService)
  def codeModule: GuiceableModule = bind[CodeGenerator].toInstance(generatorMock)
}

trait RealEjabberdTest extends GuiceOverrides {
  override implicit val smackUtils: SmackUtils = new SmackUtils
  override def ejabberdModule = bind[SmackUtils].toInstance(smackUtils)
}

trait ErrorEjabberdTest extends GuiceOverrides {
  override implicit val smackUtils: SmackErrorMock = new SmackErrorMock
  override def ejabberdModule = bind[SmackUtils].toInstance(smackUtils)
}

trait RealPushNotificationsTest extends GuiceOverrides {
  override implicit val pushNotificationService = new PushNotificationService
  override def pushNotificationsModule = bind[PushNotificationService].toInstance(pushNotificationService)
}