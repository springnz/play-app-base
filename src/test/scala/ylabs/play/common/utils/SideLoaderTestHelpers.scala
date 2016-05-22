package ylabs.play.common.utils

import org.scalatest.{Matchers, WordSpec}
import ylabs.play.common.models.Helpers.Id

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

object SideLoadTestHelpers {
  val resultMap = Seq(
    Result(Id[Result]("t1"), Id[AnotherResult]("a1"), "test"),
    Result(Id[Result]("t2"), Id[AnotherResult]("a1"), "test2"),
    Result(Id[Result]("t3"), Id[AnotherResult]("a1"), "test3")
  )

  val anotherResultMap = Seq(
    AnotherResult(Id[AnotherResult]("a1"), "another")
  )

  implicit val sideLoadFactory = new SideLoadedFactory[TestSideLoad, TestSideLoadOptions] {
    def getDefaultOptions = TestSideLoadOptions()
    def getDefaultSideLoaded = TestSideLoad()
  }

  implicit val testAdder = new SideLoadAdder[ResultInfo, TestSideLoad] {
    def add(sideLoaded: TestSideLoad, item: ResultInfo) = sideLoaded.copy(testResults = sideLoaded.testResults + (item.id -> item), resultIds = sideLoaded.resultIds + item.id)
  }

  implicit val anotherResultsAdder = new SideLoadAdder[AnotherResultInfo, TestSideLoad] {
    def add(sideLoaded: TestSideLoad, item: AnotherResultInfo) = sideLoaded.copy(anotherResults = sideLoaded.anotherResults + (item.id -> item), resultIds = sideLoaded.resultIds + item.id)
  }

  implicit val anotherTestLoader = new Loader[AnotherResult,AnotherResultInfo] {
    def get(id: Id[AnotherResult]) = Future { anotherResultMap.find(_.id == id) }
    def toResponse(model: AnotherResult) = model.toInfo
    def idExtract(model: AnotherResult) = model.id
  }
  implicit val anotherSideLoader = new SideLoader[AnotherResultInfo, TestSideLoad, TestSideLoadOptions] {
    def get(model: AnotherResultInfo, options: TestSideLoadOptions)(implicit ec: ExecutionContext): Future[TestSideLoad] = {
      Future{TestSideLoad()}
    }
  }

  implicit val testLoader = new Loader[Result, ResultInfo] {
    def get(id: Id[Result]) = Future { resultMap.find(_.id == id) }
    def toResponse(model: Result) = model.toInfo
    def idExtract(model: Result) = model.id
  }
  implicit val testSideLoader = new SideLoader[ResultInfo, TestSideLoad, TestSideLoadOptions] {
    def get(model: ResultInfo, options: TestSideLoadOptions)(implicit ec: ExecutionContext): Future[TestSideLoad] = {
      getModelFuture(Set(model.another), options.anotherResults).map(another => TestSideLoad(anotherResults = another))
    }
  }
}



class TestSideLoadProcessor extends SideLoaderProcessor[TestSideLoad, TestSideLoadOptions] {
  import SideLoadTestHelpers._

  protected def mapFutures(loaded: TestSideLoad, options: TestSideLoadOptions) =
    Set[Option[Future[TestSideLoad]]](
      options.testResults match { case true => Some(merge(loaded.testResults, options)) case _ => None },
      options.anotherResults match { case true => Some(merge(loaded.anotherResults, options)) case _ => None }
    )
}

case class ResultInfo(id: Id[Result], another: Id[AnotherResult], value: String)
case class Result(id: Id[Result], another: Id[AnotherResult], value: String)  {
  def toInfo = ResultInfo(id, another, value)
}

case class AnotherResultInfo(id: Id[AnotherResult], value: String)
case class AnotherResult(id: Id[AnotherResult], value: String) {
  def toInfo = AnotherResultInfo(id, value)
}

case class TestSideLoad(testResults: Map[Id[Result], ResultInfo] = Map(),
                       anotherResults: Map[Id[AnotherResult], AnotherResultInfo] = Map(),
                        resultIds: Set[Id[Any]] = Set()) extends SideLoaded[TestSideLoad] {

  def ++(that: TestSideLoad): TestSideLoad= {
    TestSideLoad(
      testResults ++ that.testResults,
      anotherResults ++ that.anotherResults
    )
  }

  def +[T](that: T)(implicit adder: SideLoadAdder[T, TestSideLoad]): TestSideLoad =
    adder.add(this, that)

  def +=[T](that: Iterable[T])(implicit adder: SideLoadAdder[T, TestSideLoad]): TestSideLoad = {
    that.foldLeft(this)((res, t) => adder.add(res, t))
  }
}

case class TestSideLoadOptions(testResults: Boolean = false, anotherResults: Boolean = false) extends SideLoadedOptions {
  def this(options: Map[String, Boolean]) =
    this(
      options.getOrElse("testResults", false),
      options.getOrElse("anotherResults", false)
    )
}
