package ylabs.play.common.utils

import java.time.ZonedDateTime

import play.api.libs.json.{Format, JsResult, JsString, JsValue}

object UtilJsonFormats {
  implicit def dateConverter = new Format[ZonedDateTime] {
    override def reads(json: JsValue): JsResult[ZonedDateTime] =
      json.validate[String].map(ZonedDateTime.parse)

    override def writes(o: ZonedDateTime): JsValue =
      //zoned date time has iso format and then [UTC] or [Pacific/Auckland] etc which we need to remove
      JsString(o.toString.replaceFirst("\\[[^\\]]+\\]", ""))
  }
}
