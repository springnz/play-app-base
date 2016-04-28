package ylabs.play.common.test

import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatestplus.play.WsScalaTestClient

abstract class MyPlaySpec extends WordSpec with Matchers with OptionValues with WsScalaTestClient
