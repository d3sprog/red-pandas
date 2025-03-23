val scala3Version = "3.6.3"

lazy val root = project
  .in(file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "red-pandas",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
      "com.github.scopt" %% "scopt" %  "4.1.0",
      "org.http4s" %% "http4s-blaze-client" % "0.23.15",
      "org.http4s" %% "http4s-circe" % "0.23.15",
      "io.circe" %% "circe-generic" % "0.14.5",
      "io.circe" %% "circe-parser" % "0.14.5",
      "org.zeromq" % "jeromq" % "0.5.3",  // Pure Java implementation of ZeroMQ
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.14.2",
      "commons-codec" % "commons-codec" % "1.15",
    ),

    // Native image settings
    Compile / mainClass := Some("red_pandas.main"), // Replace with your actual main class
    nativeImageOptions ++= Seq(
      "--no-fallback",
      "--initialize-at-build-time",
      "-H:+ReportExceptionStackTraces"
    ),
  )
