package ylabs.play.common

import javax.inject.Inject

import play.api.http.{DefaultHttpRequestHandler, HttpConfiguration, HttpErrorHandler, HttpFilters}
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.Router
import ylabs.play.common.controllers.{FileUploadController, HealthcheckController, LocationController, UserController}

class RequestHandler @Inject() (
  router: Router,
  errorHandler: HttpErrorHandler,
  configuration: HttpConfiguration,
  filters: HttpFilters,
  userController: UserController,
  locationController: LocationController,
  fileUploadController: FileUploadController,
  healthcheckController: HealthcheckController
  ) extends DefaultHttpRequestHandler(router , errorHandler, configuration, filters) {

  override def routeRequest(req: RequestHeader): Option[Handler] = {
    (req.method, req.path) match {
      case ("GET", "/auth") ⇒ Some(userController.authenticate())
      case ("POST", "/user") ⇒ Some(userController.create())
      case ("POST", "/user/code/request") => Some(userController.requestCode())
      case ("POST", "/user/code/register") => Some(userController.registerDevice())
      case ("PATCH", "/user") ⇒ Some(userController.update())
      case ("GET", "/user") ⇒ Some(userController.get())
      case ("POST", "/location") ⇒ Some(locationController.update())
      case ("GET", "/location") ⇒ Some(locationController.list())
      case ("GET", "/location/last") ⇒ Some(locationController.last())
      case ("POST", "/location/nearby") ⇒ Some(locationController.nearby())
      case ("POST", "/location/suggest") ⇒ Some(locationController.suggest())
      case ("POST", "/cloud") ⇒ Some(fileUploadController.uploadFile())
      case ("GET", "/healthcheck") ⇒ Some(healthcheckController.check())
      case _ ⇒ None
    }
  }
}
