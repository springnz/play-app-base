package ylabs.play.common.models

import com.nimbusds.jwt.JWTClaimsSet
import gremlin.scala._
import play.api.libs.json.Json
import ylabs.play.common.models.Helpers.{ApiRequest, ApiResponse, IDJson, Id}
import ylabs.play.common.utils.JWTUtil.JWT

object User {
  @label("user")
  case class User(id: Id[User],
      name: Name,
      status: Status,
      phone: Option[Phone] = None,
      email: Option[Email] = None,
      deviceEndpoint: Option[DeviceEndpoint] = None) {
    def toUserInfoResponse(jwt: JWT) = UserInfoResponse(id, name, jwt, phone, email)
    def toMinimalUser = MinimalUser(id, name, status, phone, email)
  }

  case class Name(value: String) extends AnyVal
  case class Status(value: String) extends AnyVal
  case class Phone(value: String) extends AnyVal
  case class Email(value: String) extends AnyVal
  case class DeviceEndpoint(value: String) extends AnyVal

  case class MinimalUser(
      id: Id[User],
      name: Name,
      status: Status,
      phone: Option[Phone] = None,
      email: Option[Email] = None)

  implicit val userNameFormat = IDJson(Name)(Name.unapply)
  implicit val userStatusFormat = IDJson(Status)(Status.unapply)
  implicit val userPhoneFormat = IDJson(Phone)(Phone.unapply)
  implicit val userEmailFormat = IDJson(Email)(Email.unapply)
  implicit val userDeviceEndpointFormat = IDJson(DeviceEndpoint)(DeviceEndpoint.unapply)
  implicit val minimalUserFormat = Json.format[MinimalUser]

  def phoneFromClaims(claims: JWTClaimsSet) = Phone(claims.getClaim(JwtClaims.Phone).toString)
  def nameFromClaims(claims: JWTClaimsSet) = Name(claims.getClaim(JwtClaims.Name).toString)

  val Registered = "Registered"
  val Invited = "Invited"

  val Label = "user"
  object Properties {
    object Id extends Key[String]("id")
    object Name extends Key[String]("name")
    object Status extends Key[String]("status")
    object Phone extends Key[String]("phone")
    object Email extends Key[String]("email")
    object DeviceEndpoint extends Key[String]("deviceEndpoint")
  }

  object JwtClaims {
    val Name = "name"
    val Phone = "phone"
    val Email = "email"
  }

  def apply(v: Vertex) = v.toCC[User]

  case class UserUpdateRequest(phone: Option[Phone] = None, email: Option[Email] = None, name: Option[Name] = None,
    locationSharing: Option[Boolean] = None) extends ApiRequest
  case class RegistrationRequest(phone: Phone, email: Option[Email], name: Name) extends ApiRequest

  case class UserInfoResponse(id: Id[User], name: Name, jwt: JWT, phone: Option[Phone],
    email: Option[Email] = None) extends ApiResponse

  implicit val userUpdateResponseFormat = Json.format[UserUpdateRequest]
  implicit val userRegistrationRequestFormat = Json.format[RegistrationRequest]
  implicit val userInfoResponseFormat = Json.format[UserInfoResponse]
}
