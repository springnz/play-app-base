package ylabs.play.common.controllers

import javax.inject.Inject

import akka.util.Timeout
import com.google.firebase.auth.FirebaseAuth
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import springnz.util.Logging
import ylabs.play.common.dal.UserRepository
import ylabs.play.common.firebase.rest.UserFirebaseRest
import ylabs.play.common.models.Helpers.ApiFailure.Failed
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.PushNotification.{Platform, Token}
import ylabs.play.common.models.Sms.Text
import ylabs.play.common.models.User
import ylabs.play.common.models.User._
import ylabs.play.common.models.ValidationError.{Field, Invalid, Reason}
import ylabs.play.common.services.{PushNotificationService, SmsService}
import ylabs.play.common.utils.JWTUtil.JWT
import ylabs.play.common.utils._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object UserController {
  def processError(failure: FailureType): Failed = {
    val invalid = failure match {
      case FailureType.DeviceCodeDoesNotMatch ⇒
        Invalid(Field("code"), Reason("DoesNotMatch"))
      case FailureType.DeviceIdDoesNotMatch ⇒
        Invalid(Field("deviceId"), Reason("DoesNotMatch"))
      case FailureType.DeviceNotActivated ⇒
        Invalid(Field("device"), Reason("NotActivated"))
      case FailureType.RecordNotFound ⇒
        Invalid(Field("user"), Reason("NotFound"))
      case FailureType.DeviceCodeMissing =>
        Invalid(Field("code"), Reason("Missing"))

    }
    Failed(List(invalid))
  }
}

class UserController @Inject() (
    repo: UserRepository,
    firebase: UserFirebaseRest,
    pushService: PushNotificationService,
    smsService: SmsService,
    configuration: Configuration,
    authenticated: Authenticated,
    nonActiveAuthenticated: NonActiveAuthenticated,
    codeGenerator: CodeGenerator,
    jwtUtil: JWTUtil)(implicit ec: ExecutionContext) extends Controller with Logging {

  implicit val timeout: Timeout = 5.seconds

  def getToken(user: User): JWT = {

    val firebaseToken = FirebaseAuth.getInstance().createCustomToken(user.id.value)
    println(s"fbtoken:$firebaseToken")
    jwtUtil.issueToken(user.id,
      JwtClaims.Phone -> user.phone.getOrElse(Phone("")).value,
      JwtClaims.Email -> user.email.getOrElse(Email("")).value,
      JwtClaims.Name → user.name.value,
      JwtClaims.FirebaseToken → firebaseToken)
  }

  def create() = Action.async(parse.json[RegistrationRequest]) { request ⇒
    // what's your name? cookie monster? ok sure, come in :)
    val registration = request.body
    val name = registration.name

    val deviceIdOption = request.headers.get("Device-Id").map(DeviceId)

    (deviceIdOption, PhoneValidator.validate(registration.phone)) match {
      case (Some(deviceId), Some(phone)) ⇒
        repo.getFromPhone(phone) flatMap {
          case None ⇒
            for {
              userFirebaseId ← firebase.create(phone, name, User.Status(User.Registered))
              user ← repo.createFromPhone(phone, User.Status(User.Registered), name, Some(deviceId), Some(userFirebaseId))
            } yield Ok(Json.toJson(user.toUserInfoResponse(getToken(user))))
          case Some(user) ⇒

            firebase.update(user.id, Some(name), Some(User.Status(User.Registered)))

            val devicePlatform = Platform(request.headers.get("Device-Platform").getOrElse(""))
            val deviceToken = Token(request.headers.get("Device-Push-Identifier").getOrElse(""))
            repo.loginWithPhone(phone, name, User.Status(User.Registered), deviceId).flatMap {
              case Right(u) ⇒
                pushService.register(devicePlatform, deviceToken, u.deviceEndpoint)
                  .map(_.map(endpoint => repo.setPushEndpoint(u.id, endpoint)))
                val jwt = getToken(u)
                Future { Ok(Json.toJson(u.toUserInfoResponse(jwt))) }

              case Left(fail) ⇒
                repo.clearDevice(user.id).map(u => Ok(Json.toJson(u.toUserInfoResponse(getToken(u)))))
            }
        }

      case (_, None) ⇒
        val invalid = Invalid(Field("phone"), Reason("Invalid NZ mobile number"))
        val failure = Failed(List(invalid))
        Future.successful(BadRequest(Json.toJson(failure)))

      case (None, _) =>
        val invalid = Invalid(Field("Device-Id"), Reason("Missing device id"))
        val failure = Failed(List(invalid))
        Future.successful(BadRequest(Json.toJson(failure)))
    }
  }

  def requestCode() = nonActiveAuthenticated.async { request ⇒
    val deviceId = request.headers.get("Device-Id").map(DeviceId)

    deviceId match {
      case None ⇒
        val invalid = Invalid(Field("Device-Id"), Reason("Missing device id"))
        val failure = Failed(List(invalid))
        Future { BadRequest(Json.toJson(failure)) }
      case Some(id) ⇒
        repo.setDeviceCode(request.user.phone.get, codeGenerator.createCode(), id).map {
          case Left(fail) ⇒
            BadRequest(Json.toJson(UserController.processError(fail)))

          case Right(User.User(_, name, _, _, _, Some(phone), _, _, Some(code), _)) ⇒
            val messageText = configuration.config
              .getString("auth.confirmationText").format(name.value, code.value)
            smsService.send(phone, Text(messageText))
            Ok

          case _ ⇒ InternalServerError
        }
    }
  }

  def registerDevice() = nonActiveAuthenticated.async(parse.json[RegisterDeviceRequest]) { request ⇒
    val deviceId = request.headers.get("Device-Id").map(DeviceId)
    deviceId match {
      case None ⇒
        val invalid = Invalid(Field("Device-Id"), Reason("Missing device id"))
        val failure = Failed(List(invalid))
        Future { BadRequest(Json.toJson(failure)) }
      case Some(id) ⇒
        repo.registerDevice(request.user.phone.get, request.body.code, id).map {
          case Left(fail) ⇒
            BadRequest(Json.toJson(UserController.processError(fail)))
          case Right(u) ⇒
            val devicePlatform = Platform(request.headers.get("Device-Platform").getOrElse(""))
            val deviceToken = Token(request.headers.get("Device-Push-Identifier").getOrElse(""))
            pushService.register(devicePlatform, deviceToken, u.deviceEndpoint)
              .map(_.map(endpoint => repo.setPushEndpoint(u.id, endpoint)))
            Ok(Json.toJson(u.toUserInfoResponse(getToken(u))))
        }
    }
  }

  def get() = authenticated.async { request ⇒
    val user = request.user
    val jwt = getToken(user)
    Future { Ok(Json.toJson(user.toUserInfoResponse(jwt))) }
  }

  def getUserInfo(id: Id[User]) = authenticated.async { request ⇒
    repo.get(id).map {
      case Some(user) ⇒
        val userInfoResponse = user.toMinimalUser
        Ok(Json.toJson(userInfoResponse))
      case None ⇒ NotFound
    } recover {
      case t ⇒ log.error(s"Error getting user ${id.value}", t); throw t
    }
  }

  def authenticate() = authenticated.async { request ⇒
    Future.successful(Ok)
  }

  def update() = authenticated.async(parse.json[UserUpdateRequest]) { request ⇒
    repo.update(request.user.id, request.body.phone, request.body.email, request.body.name, None)
      .map {
        case Right(user) ⇒
          val jwt = getToken(user)
          Ok(Json.toJson(UserInfoResponse(user.id, user.name, jwt, user.phone, user.deviceActivated, user.email)))
        case Left(fail) ⇒ Unauthorized(Json.toJson(UserController.processError(fail)))
      } recover {
        case t ⇒ log.error(s"Error updating user ${request.user.id}", t); throw t
      }
  }

}