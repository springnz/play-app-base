package ylabs.play.common.test.mocks

import ylabs.play.common.utils.SmackModels.{Room, Username}
import ylabs.play.common.utils.SmackUtils

import scala.collection.mutable

class SmackErrorMock extends SmackUtils {

  val toRun: mutable.Set[String] = mutable.Set()
  override def createChatRoom(room: Room) = if (!toRun.contains("createChatRoom")) throw new UnsupportedOperationException else super.createChatRoom(room)
  override def destroyChatRoom(room: Room) = if (!toRun.contains("destroyChatRoom")) throw new UnsupportedOperationException else super.destroyChatRoom(room)
  override def registerWithChatRoom(username: Username, room: Room) = if (!toRun.contains("registerWithChatRoom")) throw new UnsupportedOperationException else super.registerWithChatRoom(username, room)
  override def removeFromChatRoom(username: Username, room: Room) = if (!toRun.contains("removeFromChatRoom")) throw new UnsupportedOperationException else super.removeFromChatRoom(username, room)
  override def getChatRooms = if (!toRun.contains("getChatRooms")) throw new UnsupportedOperationException else super.getChatRooms
  override def getChatRoomMembers(room: Room) = if (!toRun.contains("getChatRoomMembers")) throw new UnsupportedOperationException else super.getChatRoomMembers(room)
}