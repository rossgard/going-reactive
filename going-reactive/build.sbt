name := """going-reactive"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.1"

parallelExecution in ThisBuild := false

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.3",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test",
  "org.scalatest" % "scalatest_2.11" % "2.1.7" % "test"
)

