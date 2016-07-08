name := "play-app-base"
organization := "ylabs"
scalaVersion := "2.11.8"

val playVersion = "2.5.2"
val smackVersion = "4.1.3"
val awsVersion = "1.10.34"

val repo = "https://nexus.prod.corp/content"
resolvers ++= Seq(
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  "spring" at s"$repo/groups/public",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  Resolver.mavenLocal
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % playVersion,
  "com.typesafe.play" %% "filters-helpers" % playVersion,
  "com.typesafe.play" %% "play-logback" % playVersion,
  "com.michaelpollmeier" %% "gremlin-scala" % "3.1.1-incubating.1",
  "com.michaelpollmeier" % "orientdb-gremlin" % "3.1.1-incubating.1",
  "springnz" %% "util-lib" % "2.10.0",
  "springnz" %% "orientdb-migrations" % "2.8.0",
  "org.igniterealtime.smack" % "smack-java7" % smackVersion,
  "org.igniterealtime.smack" % "smack-tcp" % smackVersion,
  "org.igniterealtime.smack" % "smack-im" % smackVersion,
  "org.igniterealtime.smack" % "smack-extensions" % smackVersion,
  "com.twilio.sdk" % "twilio-java-sdk" % "5.2.1",
  "com.nimbusds" % "nimbus-jose-jwt" % "4.2",
  "com.amazonaws" % "aws-java-sdk-s3" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-sns" % awsVersion,
  "com.google.maps" % "google-maps-services" % "0.1.12",
  "org.julienrf" %% "play-json-derived-codecs" % "3.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0",
  "org.mockito" % "mockito-core" % "1.10.19",
  "com.google.firebase" % "firebase-server-sdk" % "3.0.1"
)

fork := true

javaOptions += "-Duser.timezone=UTC"

publishTo <<= version { (v: String) ⇒
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at s"$repo/repositories/snapshots")
  else Some("releases" at s"$repo/repositories/releases")
}

releaseSettings
ReleaseKeys.versionBump := sbtrelease.Version.Bump.Minor
ReleaseKeys.tagName := s"${name.value}-v${version.value}"

scalacOptions ++= Seq("-Xlint", "-deprecation")
