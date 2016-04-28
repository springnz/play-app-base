package ylabs.play.common.models

object PushNotification {
  case class Notification(endpoint: User.DeviceEndpoint,
                          id: Id,
                          name: Name,
                          title: Title,
                          text: Text,
                          link: Link,
                          linkName: LinkName,
                          formattedMessage: Option[FormattedMessage])
  case class Id(value: Int)
  case class Name(value: String)
  case class Text(value: String)
  case class Title(value: String)
  case class Link(value: String)
  case class LinkName(value: String)
  case class FormattedMessage(value: String)

  case class Platform(value: String)
  case class PlatformEndpoint(value: String)
  case class Token(value: String)
}
