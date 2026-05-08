ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"

lazy val root = (project in file("."))
  .settings(
    name := "hustlers-ljubljana-bot",
    libraryDependencies ++= Seq(
      // MongoDB Java Driver (синхронный)
      "org.mongodb" % "mongodb-driver-sync" % "5.1.0",
      "io.github.kirill5k" %% "mongo4cats-core" % "0.7.2",
      "io.github.kirill5k"    %% "mongo4cats-circe"  % "0.7.2",
      "io.circe"              %% "circe-generic"     % "0.14.6",

      // Core
      "org.typelevel" %% "cats-effect" % "3.5.4",

      // Конфигурация (pureconfig)
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.7",
      "com.github.pureconfig" %% "pureconfig-generic" % "0.17.7",

      // JSON
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2",

      // Логирование
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
    )
  )

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked"
)