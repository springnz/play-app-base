package ylabs.play.common.models

object Notify {
  case class Notify(name: Name,
                    title: Title,
                    text: Text,
                    link: Option[Link] = None,
                    linkName: Option[LinkName] = None,
                    notificationId: Option[PushNotification.Id] = None,
                    formattedMessage: Option[FormattedMessage] = None )
  case class Name(value: String)
  case class Title(value: String)
  case class Text(value: String)
  case class Link(value: String)
  case class LinkName(value: String)
  case class FormattedMessage(value: String)
}