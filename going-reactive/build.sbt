name := """going-reactive"""

version := "1.0-SNAPSHOT"

parallelExecution in ThisBuild := false

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.3",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test",
  "org.scalatest" % "scalatest_2.10" % "2.1.0" % "test"
)

