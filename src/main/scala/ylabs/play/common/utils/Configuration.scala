package ylabs.play.common.utils

import javax.inject.Singleton

import com.typesafe.config.ConfigFactory

@Singleton
class Configuration {
  lazy val config = ConfigFactory.load()
}