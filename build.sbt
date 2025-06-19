val scala3Version = "3.7.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "pgk-anonbot",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test
    )
  )
