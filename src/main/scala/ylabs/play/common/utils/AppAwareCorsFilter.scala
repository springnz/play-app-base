package ylabs.play.common.utils

import javax.inject.Inject

import akka.stream.Materializer
import play.api.{ Configuration ⇒ PlayConfiguration }
import play.api.mvc.{ RequestHeader, Result }
import play.filters.cors.{ CORSConfig, CORSFilter }

import scala.concurrent.Future

class AppAwareCorsFilter @Inject() (implicit mat: Materializer, configuration: PlayConfiguration) extends CORSFilter(corsConfig = CORSConfig.fromConfiguration(configuration)) {
  override def apply(f: RequestHeader ⇒ Future[Result])(request: RequestHeader): Future[Result] = {
    if (request.headers.get("Origin") contains "file://") f(request)
    else super.apply(f)(request)
  }
}
