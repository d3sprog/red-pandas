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
      "com.github.scopt" %% "scopt" %  "4.1.0"
    ),

    // Native image settings
    Compile / mainClass := Some("red_pandas.main"), // Replace with your actual main class
    nativeImageOptions ++= Seq(
      "--no-fallback",
      "--initialize-at-build-time",
      "-H:+ReportExceptionStackTraces"
    ),
  )
