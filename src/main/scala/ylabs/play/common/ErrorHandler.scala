package ylabs.play.common

import java.io.{PrintWriter, StringWriter}
import javax.inject.{Inject, Provider}

import play.api.http.DefaultHttpErrorHandler
import play.api.libs.json.{JsObject, JsString, Json, Writes}
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import play.api.routing.Router
import play.api.{Configuration, Environment, OptionalSourceMapper, UsefulException}
import ylabs.play.common.models.Helpers.ApiFailure.Failed
import ylabs.play.common.models.ValidationError.{Field, Invalid, Reason}

import scala.concurrent.Future

class ErrorHandler @Inject() (
    env: Environment,
    config: Configuration,
    sourceMapper: OptionalSourceMapper,
    router: Provider[Router]) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  def prettyTrace(t: Throwable) = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  implicit def exceptionWrites: Writes[Throwable] = new Writes[Throwable] {
    def writes(t: Throwable) = JsObject(Seq(
      "message" -> JsString(t.getMessage),
      "trace" -> JsString(prettyTrace(t))))
  }

  override def onDevServerError(request: RequestHeader, exception: UsefulException) = {
    Future.successful(InternalServerError(Json.toJson(exception)))
  }

  override def onProdServerError(request: RequestHeader, exception: UsefulException) = {
    Future.successful(InternalServerError(Json.toJson("Internal Error")))
  }

  override def onBadRequest(request: RequestHeader, message: String) = {
    def errorToMessage(error: String) = error match {
      case "error.path.missing" ⇒ "Value missing"
      case _                    ⇒ error
    }

    //This is a bit of a mess because play doesn't allow invalid json to get to our client code
    //Message will be something like "Json validation error List((obj.name,List(ValidationError(List(error.path.missing),WrappedArray()))))"
    //So this extracts the field and reason
    val errorRegex = """\(obj.([^,]+),List\(ValidationError\(List\(([^\)]+)""".r
    val errors = errorRegex.findAllMatchIn(message) map { m ⇒ Invalid(Field(m.group(1)), Reason(errorToMessage(m.group(2)))) }
    val failed = Failed(errors.toList)
    Future.successful(BadRequest(Json.toJson(failed)))
  }
}