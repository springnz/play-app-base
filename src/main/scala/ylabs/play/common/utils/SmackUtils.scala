package ylabs.play.common.utils

import java.util.UUID

import com.typesafe.config.ConfigFactory
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.packet.XMPPError
import org.jivesoftware.smack.tcp.{XMPPTCPConnection, XMPPTCPConnectionConfiguration}
import org.jivesoftware.smackx.muc.{MultiUserChat, MultiUserChatManager}
import ylabs.play.common.utils.SmackModels._

import scala.collection.JavaConversions._

sealed trait SmackError extends Exception

object SmackModels {

  case class InvalidUserName(username: Username) extends SmackError

  case class DuplicateUser(username: Username) extends SmackError

  case class RoomAlreadyExists(room: Room) extends SmackError

  object Forbidden extends SmackError

  case class Username(value: String) extends AnyVal

  case class Room(value: String) extends AnyVal

  case class Password(value: String) extends AnyVal

}

class SmackUtils {
  object ConfigKeys {
    val domain = "messaging.domain"
    val host = "messaging.host"
    val service = "messaging.service"
    val adminUsername = "messaging.admin.username"
    val adminPassword = "messaging.admin.password"
  }

  lazy val config = ConfigFactory.load()
  lazy val domain = config.getString(ConfigKeys.domain)
  lazy val chatService = config.getString(ConfigKeys.service)
  lazy val host = config.getString(ConfigKeys.host)
  lazy val adminUsername = Username(config.getString(ConfigKeys.adminUsername))
  lazy val adminPassword = Password(config.getString(ConfigKeys.adminPassword))

  private def fullUser(user: Username) = s"${user.value}@$domain"

  private def withConnection[T](username: Option[Username] = None, password: Option[Password] = None)(block: XMPPTCPConnection ⇒ T) = {
    val connection = new XMPPTCPConnection(
      XMPPTCPConnectionConfiguration.builder
        .setUsernameAndPassword(username.getOrElse(adminUsername).value, password.getOrElse(adminPassword).value)
        .setServiceName(domain)
        .setHost(host)
        .setSecurityMode(SecurityMode.disabled)
        .setSendPresence(true)
        .setResource(UUID.randomUUID().toString)
        .build)
    try {
      connection.connect().login()
      block(connection)
    } finally {
      connection.disconnect()
    }
  }

  private def withChatRoom[T](room: Room)(block: MultiUserChat ⇒ T) = withConnection() { connection ⇒
    val id = s"${room.value}@$chatService"
    val manager = MultiUserChatManager.getInstanceFor(connection).getMultiUserChat(id.toString)
    block(manager)
  }

  def createChatRoom(room: Room): Unit = withChatRoom(room) { chat ⇒
    try {
      chat.create("admin")
      val form = chat.getConfigurationForm.createAnswerForm
      form.setAnswer("muc#roomconfig_membersonly", true)
      chat.sendConfigurationForm(form)
    } catch {
      case ex: XMPPErrorException if ex.getXMPPError.getCondition == XMPPError.Condition.forbidden && ex.getXMPPError.getType == XMPPError.Type.AUTH ⇒ throw Forbidden
      case ex: IllegalStateException if ex.getMessage == "Creation failed - User already joined the room." ⇒ throw new RoomAlreadyExists(room)
      case ex: SmackException if ex.getMessage == "Creation failed - Missing acknowledge of room creation." ⇒ throw new RoomAlreadyExists(room)
      case t: Throwable ⇒ throw t
    }
  }

  def destroyChatRoom(room: Room) = withChatRoom(room) { chat ⇒
    try {
      chat.destroy("destroying room", null)
    } catch {
      case ex: XMPPErrorException if ex.getXMPPError.getCondition == XMPPError.Condition.forbidden && ex.getXMPPError.getType == XMPPError.Type.AUTH ⇒ throw Forbidden
      case ex: XMPPErrorException if ex.getXMPPError.getCondition == XMPPError.Condition.item_not_found && ex.getXMPPError.getType == XMPPError.Type.CANCEL ⇒
      case t: Throwable ⇒ throw t
    }
  }

  def registerWithChatRoom(username: Username, room: Room) = withChatRoom(room) { chat ⇒
    try {
      chat.grantMembership(fullUser(username))
    } catch {
      case ex: XMPPErrorException if ex.getXMPPError.getCondition == XMPPError.Condition.not_allowed && ex.getXMPPError.getType == XMPPError.Type.CANCEL ⇒ throw Forbidden
      case t: Throwable ⇒ throw t
    }
  }

  def removeFromChatRoom(username: Username, room: Room) = withChatRoom(room) { chat ⇒
    try {
      chat.revokeMembership(fullUser(username))
    } catch {
      case ex: XMPPErrorException if ex.getXMPPError.getCondition == XMPPError.Condition.not_allowed && ex.getXMPPError.getType == XMPPError.Type.CANCEL ⇒ throw Forbidden
      case t: Throwable ⇒ throw t
    }
  }

  def getChatRooms(): Set[Room] = withConnection() { connection ⇒
    MultiUserChatManager.getInstanceFor(connection).getHostedRooms(chatService).map(c ⇒ Room(c.getJid)).toSet
  }

  def getChatRoomMembers(room: Room): Set[Username] = withChatRoom(room) { chat ⇒
    try {
      chat.getMembers.map(m ⇒ Username(m.getJid)).toSet
    } catch {
      case ex: XMPPErrorException if ex.getXMPPError.getCondition == XMPPError.Condition.forbidden && ex.getXMPPError.getType == XMPPError.Type.AUTH ⇒ throw Forbidden
      case t: Throwable ⇒ throw t
    }
  }
}
