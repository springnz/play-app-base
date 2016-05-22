package ylabs.play.common.utils

import ylabs.play.common.models.Helpers.Id

import scala.concurrent.{ExecutionContext, Future}

trait SideLoadedOptions
trait SideLoaded[Loaded <: SideLoaded[Loaded]] {
  def ++(that: Loaded): Loaded
  def +[T](that: T)(implicit adder: SideLoadAdder[T, Loaded]): Loaded
  def +=[T](that: Iterable[T])(implicit adder: SideLoadAdder[T, Loaded]): Loaded
}

trait SideLoadedFactory[Loaded <: SideLoaded[Loaded], Options <: SideLoadedOptions] {
  def getDefaultOptions: Options
  def getDefaultSideLoaded: Loaded
}
trait SideLoadAdder[T, Loaded <: SideLoaded[Loaded]] {
  def add(sideLoaded: Loaded, item: T): Loaded
}

trait Loader[T, U] {
  def get(id: Id[T]): Future[Option[T]]
  def toResponse(model: T):U
  def idExtract(model: T): Id[T]
}

trait SideLoader[T, Loaded <: SideLoaded[Loaded], Options <: SideLoadedOptions] {
  def get(response: T, options: Options)(implicit ec: ExecutionContext): Future[Loaded]

  def getModelFuture[A, B](ids: Iterable[Id[A]], include: Boolean)
                          (implicit loader: Loader[A, B], ec
                          : ExecutionContext): Future[Map[Id[A], B]] = {
    include match {
      case true => getModels(ids)
      case false => Future.successful(Map())
    }
  }

  def getModels[A, B]
  (ids: Iterable[Id[A]])
  (implicit loader: Loader[A, B], ec: ExecutionContext)
  : Future[Map[Id[A], B]] = {

    Future.sequence(ids.map(loader.get)).map(
      _.filter(_.isDefined)
        .map(_.get)
        .map(ret => loader.idExtract(ret) -> loader.toResponse(ret))
        .toMap
    )
  }
}


trait SideLoaderProcessor[Loaded <: SideLoaded[Loaded], Options <: SideLoadedOptions] {
  protected def mapFutures(loaded: Loaded, options: Options): Set[Option[Future[Loaded]]]
  protected def merge[A,B](map: Map[Id[A],B], options: Options)(implicit sideLoader: SideLoader[B, Loaded, Options],
                                                                sideLoadedFactory: SideLoadedFactory[Loaded, Options],
                                                                ec: ExecutionContext) =
    Future.sequence(map.map(a => getAll(a._2, options))).map(s => s.foldLeft(sideLoadedFactory.getDefaultSideLoaded)(_ ++ _))


  private def getAll[U](response: U, options: Options)(implicit sideLoader: SideLoader[U, Loaded, Options],
                                                       sideLoadedFactory: SideLoadedFactory[Loaded, Options],
                                                       ec: ExecutionContext): Future[Loaded] = {
    def getToMerge(loaded: Loaded) = {
      mapFutures(loaded, options).filter(_.isDefined).map(_.get)
    }

    sideLoader.get(response, options).flatMap { newLoader =>
      Future.sequence(getToMerge(newLoader)).map(_.foldLeft(newLoader)(_ ++ _))
    }
  }

  def loadMany[T](response: Iterable[T], options: Option[Options])
                 (implicit sideLoader: SideLoader[T, Loaded, Options],
                  sideLoadAdder: SideLoadAdder[T, Loaded],
                  sideLoadedFactory: SideLoadedFactory[Loaded, Options],
                  ec: ExecutionContext) : Future[Loaded] = {
    val sideLoadOptions = options.getOrElse(sideLoadedFactory.getDefaultOptions)
    val futures = response.map(r => getAll(r, sideLoadOptions))
    Future.sequence(futures) map {_.foldLeft(sideLoadedFactory.getDefaultSideLoaded)(_ ++ _)} map { _ += response }
  }

  def load[T](response:T, options: Option[Options])
             (implicit sideLoader: SideLoader[T, Loaded, Options],
              sideLoadAdder: SideLoadAdder[T, Loaded],
              sideLoadedFactory: SideLoadedFactory[Loaded, Options],
              ec: ExecutionContext) : Future[Loaded] = {
    loadMany[T](Set(response),options)
  }
}