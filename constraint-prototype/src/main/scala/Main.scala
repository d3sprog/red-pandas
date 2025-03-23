package red_pandas

import java.io.File

case class Config(
    mode: String = "repl",
    files: Seq[String] = Seq.empty
) 

// by default launch the REPL
// if argument server is passed, launch the server
// if argument test is passed, run the tests
@main def main(args: String*): Unit = {
  val parser = new scopt.OptionParser[Config]("scopt") {
    head("red-pandas")
    cmd("repl")
      .action((_, c) => c.copy(mode = "repl"))
      .text("Launch the REPL")
    cmd("server")
      .action((_, c) => c.copy(mode = "server"))
      .text("Launch the server")
    cmd("test")
      .action((_, c) => c.copy(mode = "test"))
      .text("Run the tests")
    arg[String]("<file>...")
      .unbounded()
      .optional()
      .action((x, c) => c.copy(files = c.files :+ x))
      .text("files to process")
  }

  parser.parse(args, Config()) match {
    case Some(config) => config.mode match {
      case "repl" => {
        val repl = new Repl()
        for (file <- config.files) {
          repl.load_file(file)
        }
        repl.repl()
      }
      case _ => {
        println("Invalid mode " + config.mode)
        sys.exit(1)
      }
    }
    case None => {
      sys.exit(1)
    }    
  }
}
