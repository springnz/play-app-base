package ylabs.play.common.controllers

import javax.inject.Inject

import akka.util.Timeout
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import springnz.util.Logging
import ylabs.play.common.dal.UserRepository
import ylabs.play.common.models.Helpers.ApiFailure.Failed
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.PushNotification.{Platform, Token}
import ylabs.play.common.models.User
import ylabs.play.common.models.User._
import ylabs.play.common.models.ValidationError.{Field, Invalid, Reason}
import ylabs.play.common.services.PushNotificationService
import ylabs.play.common.utils.JWTUtil.JWT
import ylabs.play.common.utils.{FailureType, Authenticated, PhoneValidator, JWTUtil}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class UserController @Inject() (
    repo: UserRepository,
    pushService: PushNotificationService,
    jwtUtil: JWTUtil)(implicit ec: ExecutionContext) extends Controller with Logging {

  implicit val timeout: Timeout = 5.seconds

  def getToken(user: User): JWT = {
    jwtUtil.issueToken(user.id,
      JwtClaims.Phone -> user.phone.getOrElse(Phone("")).value,
      JwtClaims.Email -> user.email.getOrElse(Email("")).value,
      JwtClaims.Name → user.name.value)
  }

  def create() = Action.async(parse.json[RegistrationRequest]) { request ⇒
    // what's your name? cookie monster? ok sure, come in :)
    val registration = request.body
    val name = registration.name

    PhoneValidator.validate(registration.phone) match {
      case None ⇒
        val invalid = Invalid(Field("phone"), Reason("Invalid NZ mobile number"))
        val failure = Failed(List(invalid))
        Future.successful(BadRequest(Json.toJson(failure)))
      case Some(phone) ⇒
        repo.getFromPhone(phone) flatMap { user ⇒

          val devicePlatform = Platform(request.headers.get("Device-Platform").getOrElse(""))
          val deviceToken = Token(request.headers.get("Device-Push-Identifier").getOrElse(""))
          val currentEndpoint = user match {
            case Some(u) ⇒ u.deviceEndpoint
            case None    ⇒ None
          }
          val endpoint = Await.result(
            pushService.register(devicePlatform, deviceToken, currentEndpoint), 10.seconds).orElse(None)

          val res = repo.createFromPhone(phone, name, User.Status(User.Registered), endpoint).map { u ⇒
            val jwt = getToken(u)
            Ok(Json.toJson(UserInfoResponse(u.id, name, jwt, u.phone, u.email)))
          }
          res.recover {
            case t ⇒ log.error(s"Error creating user ${phone.value}", t); throw t
          }
          res
        }
    }
  }

  def get() = Authenticated.async { request ⇒
    val id = Id[User](request.user.getSubject)
    repo.get(id).map {
      case Some(user) ⇒
        val jwt = getToken(user)
        val userInfoResponse = user.toUserInfoResponse(jwt)
        Ok(Json.toJson(userInfoResponse))
      case None ⇒ NotFound
    } recover {
      case t ⇒ log.error(s"Error getting user ${id.value}", t); throw t
    }
  }

  def getUserInfo(id: Id[User]) = Authenticated.async { request =>
    repo.get(id).map {
      case Some(user) ⇒
        val userInfoResponse = user.toMinimalUser
        Ok(Json.toJson(userInfoResponse))
      case None ⇒ NotFound
    } recover {
      case t ⇒ log.error(s"Error getting user ${id.value}", t); throw t
    }
  }

  def authenticate() = Authenticated.async { request ⇒
    Future.successful(Ok)
  }

  def update() = Authenticated.async(parse.json[UserUpdateRequest]) { request ⇒
    val id = Id[User](request.user.getSubject)
    repo.update(id, request.body)
      .map {
        case Right(user) ⇒
          val jwt = getToken(user)
          Ok(Json.toJson(UserInfoResponse(user.id, user.name, jwt, user.phone, user.email)))
        case Left(FailureType.RecordNotFound) ⇒ NotFound
      } recover {
        case t ⇒ log.error(s"Error updating user ${request.user.getSubject}", t); throw t
      }
  }

}