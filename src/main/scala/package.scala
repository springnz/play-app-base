import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

package object controllers {
  implicit def resultToFuture(result: Result)(implicit ec: ExecutionContext): Future[Result] = Future(result)
}