package red_pandas

// by default launch the REPL
// if argument server is passed, launch the server
// if argument test is passed, run the tests
@main def main(): Unit = {
  val args = scala.util.Try(scala.util.Properties.envOrElse("ARGS", "")).getOrElse("")
  if (args == "server") {
    throw new Exception("Server not implemented")
  } else if (args == "test") {
    throw new Exception("Tests not implemented")
  } else {
    val repl = new Repl()
    repl.repl()
  }
}
