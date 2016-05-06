package ylabs.play.common.utils

import ylabs.play.common.models.User.Code

import scala.util.Random


class CodeGenerator {
  def createCode(): Code = Code( (0 to 3).map( i ⇒ Random.nextInt(9) ).mkString )
}
