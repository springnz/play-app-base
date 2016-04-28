package ylabs.play.common.utils

import play.api.mvc.{PathBindable, QueryStringBindable}
import ylabs.play.common.models.Helpers.Id

trait Binders {

  implicit def idBinder[T](implicit strBinder: QueryStringBindable[String]) = new QueryStringBindable[Id[T]] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Id[T]]] =
      params.get(key) flatMap {
        case x if x.isEmpty ⇒ None
        case x :: xs        ⇒ Some(Right(Id(x)))
      }

    override def unbind(key: String, id: Id[T]): String = strBinder.unbind(key, id.value)
  }

  implicit def idPathBinder[T](implicit strBinder: PathBindable[String]) = new PathBindable[Id[T]] {

    override def bind(key: String, value: String): Either[String, Id[T]] = Right(Id(value))

    override def unbind(key: String, id: Id[T]): String = strBinder.unbind(key, id.value)
  }
}
