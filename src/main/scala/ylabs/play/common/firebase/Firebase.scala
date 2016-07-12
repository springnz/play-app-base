package ylabs.play.common.firebase

import javax.inject.{Inject, Singleton}

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.inject.AbstractModule
import ylabs.play.common.utils.Configuration

class FirebaseModule extends AbstractModule {
  def configure() = {
    bind(classOf[Firebase]).asEagerSingleton()
  }
}

@Singleton
class Firebase @Inject() (conf: Configuration) {

  if (FirebaseApp.getApps.size == 0) {
    val url = conf.config.getString("firebase.url")
    val credentials = getClass.getResourceAsStream("/firebaseCredentials.json")

    val fbOptions = new FirebaseOptions.Builder()
      .setDatabaseUrl(url)
      .setServiceAccount(credentials)
      .build()

    FirebaseApp.initializeApp(fbOptions)
  }

  val db = FirebaseDatabase.getInstance()
}