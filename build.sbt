organization := "org.zalando"

name := """hutmann"""

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  json % "provided",
  ws % "provided",
  "com.lihaoyi" %% "sourcecode" % "0.1.1"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1",
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0",
  "org.scalacheck" %% "scalacheck" % "1.13.4"
) map (_ % "test")

maintainer := "team-kohle@zalando.de"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

//pom extra info
publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomExtra := (
  <scm>
    <url>git@github.com:zalando-incubator/hutmann.git</url>
    <developerConnection>scm:git:git@github.com:zalando-incubator/hutmann.git</developerConnection>
    <connection>scm:git:https://github.com/zalando-incubator/hutmann.git</connection>
  </scm>
    <developers>
      <developer>
        <name>Werner Hahn</name>
        <email>werner.hahn@zalando-payments.com</email>
        <url>https://github.com/zalando</url>
      </developer>
    </developers>)

homepage := Some(url("https://github.com/zalando-incubator/hutmann"))

//settings to compile readme
tutSettings
tutSourceDirectory := baseDirectory.value / "tut"
tutTargetDirectory := baseDirectory.value
