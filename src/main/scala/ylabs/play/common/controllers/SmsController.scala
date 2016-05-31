package ylabs.play.common.controllers

import javax.inject.Inject


import play.api.mvc.{Action, Controller}
import springnz.util.Logging
import ylabs.play.common.dal.SmsRepository
import ylabs.play.common.models.Helpers.Id
import ylabs.play.common.models.Sms.{Status => SmsStatus, Sms, ErrorCode, Smid, SmsStatusChanged}
import ylabs.play.common.models.StoredSms.StoredSms
import ylabs.play.common.services.SmsService
import scala.concurrent.{Future, ExecutionContext}

class SmsController  @Inject()  (smsService: SmsService, smsRepository: SmsRepository)(implicit ec: ExecutionContext) extends Controller with Logging  {

  def statusChanged() = Action.async(parse.urlFormEncoded) { request ⇒
    val messageSid: String = request.body("MessageSid").head
    val accountSid: String = request.body("AccountSid").head
    val to: String = request.body("To").head
    val from: String = request.body("From").head
    val messageStatus: String = request.body("MessageStatus").head
    val errorStatus: Option[String] = request.body.get("ErrorStatus").map(_.head)

    log.debug("Sms status changed", to, messageStatus, errorStatus)

    def handle(error: ErrorCode): Future[Unit] = smsService.get(Smid(messageSid)).flatMap(s => {
      error match {
        case ErrorCode("30001") => send(s, error)
        case ErrorCode("30003") => send(s, error)
        case _ => Future.successful(() => Unit)
      }
    })

    def send(sms: Sms, error: ErrorCode): Future[Unit] = {
      val changed =  SmsStatusChanged(sms.from, sms.to, sms.text, SmsStatus(messageStatus), error)
      smsRepository.create(Id[StoredSms](messageSid), changed) map  {
        case stored if stored.timesTried < 3 => smsService.send(stored.toSms)
        case _ => Future.successful(() => Unit)
      }
    }

    val toDo: Future[Any] = messageStatus match {
      case "undelivered" if errorStatus.isDefined => handle(ErrorCode(errorStatus.get))
      case "failed" if errorStatus.isDefined  => handle(ErrorCode(errorStatus.get))
      case _ => Future.successful(() => Unit)
    }

    toDo.map(_ => Ok)
  }
}