ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"

lazy val root = (project in file("."))
  .settings(
    name := "hustlers-ljubljana-bot",
    libraryDependencies ++= Seq(
      // Telegramium
      "io.github.apimorphism" %% "telegramium-core" % "7.54.1",
      "io.github.apimorphism" %% "telegramium-high" % "7.54.1",

      // HTTP (для Ollama позже)
      "com.softwaremill.sttp.client3" %% "core" % "3.9.6",
      "com.softwaremill.sttp.client3" %% "circe" % "3.9.6",

      // MongoDB Java
      "org.mongodb" % "mongodb-driver-reactivestreams" % "5.1.0",

      // Core
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.10",
      "com.github.pureconfig" %% "pureconfig-generic" % "0.17.10",
      "ch.qos.logback" % "logback-classic" % "1.4.14"
    )
  )
