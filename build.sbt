name := "HistoricalQuotes"

version := "1.0"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  ws,
  jdbc,
  "com.typesafe.play" %% "anorm" % "2.5.0"
  //"com.typesafe.play" %% "play-slick" % "2.0.2",
  //"com.h2database" % "h2" % "1.4.193"
)