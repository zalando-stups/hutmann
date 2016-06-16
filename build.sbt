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
  "org.scalatest" %% "scalatest" % "2.2.6",
  "org.scalatestplus" %% "play" % "1.4.0-M4",
  "org.scalacheck" %% "scalacheck" % "1.12.5"
) map (_ % "test")

maintainer := "team-kohle@zalando.de"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

tutSettings
tutSourceDirectory := baseDirectory.value / "tut"
tutTargetDirectory := baseDirectory.value
