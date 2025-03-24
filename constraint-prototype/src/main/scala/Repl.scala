package red_pandas

def prologToString(clauses: List[Any]): String = {
  clauses.map(_.toString()).mkString(", ") + "."
}

import scala.util.parsing.combinator.Parsers
def print_if_fail(parser: Parsers, result: parser.ParseResult[?]): Unit = {
  if (!result.successful) {
    println("Failed to parse: " + result)
  }
}

object Repl {
  def start(python: Boolean = true): Repl = {
    val env: Option[PythonEnvironment] = if (python) Some(new PythonEnvironment()) else None
    new Repl(env)
  }
}

class Repl(val env: Option[PythonEnvironment]) {
  var rules = List.empty[Rule]
  val parser = new Parser(env)

  def eval_query(input: String): Unit = {
    val parsed = try parser.parseAll(parser.query, input)
    catch case e: Exception => {
      println(e.getMessage())
      return
    }
    print_if_fail(parser, parsed)
    if (parsed.successful) {
      val results = resolve_pretty(parsed.get, this.rules)
      for (result <- results) {
        println(prologToString(result))
      }
    } else {
      println("Failed to parse query")
    }
  }

  def add_rule(input: String): Unit = {
    val parser = new Parser(env)
    val parsed = try parser.parseAll(parser.clause, input)
    catch case e: Exception => {
      println(e.getMessage())
      return
    }
    print_if_fail(parser, parsed)
    if (parsed.successful) {
      this.rules = parsed.get :: this.rules
    } else {
      println("Failed to parse rule")
    }
  }

  def load_file(filename: String): Unit = {
      val source = scala.io.Source.fromFile(filename)
      val lines = source.getLines()
      for (line <- lines) {
        println(line)
        read_eval(line)
      }
      source.close()
  }

  def read_eval(input: String): Unit = {
    if (input.isBlank()) {
      return
    }

    assert(input != null)

    if (input == ":q" || input == ":quit" || input == ":exit") {
      System.exit(0)
    } else if (input == ":rules" || input == ":r") {
      println(this.rules)
    } else if (input == ":clear" || input == ":c") {
      this.rules = List.empty
    } else if (input.startsWith(":load") || input.startsWith(":l")) {
      val filename = input.split(" ").last
      load_file(filename)
    } else if (input.startsWith(":python") || input.startsWith(":p")) {
      val code = input.split(" ").tail.mkString(" ")
      this.env match {
        case Some(env) => println(env.eval(code))
        case None => println("ERROR: Python environment not available")
      }
    } else if (input.startsWith(":")) {
      println("Unknown command " + input)
    } else if (input.startsWith("?")) {
      eval_query(input)
    } else {
      add_rule(input)
    }
  }

  def repl(): Unit = {
    println("Red Pandas REPL")
    while (true) {
      print("∫ ")
      val input = scala.io.StdIn.readLine()
      if (input == null) return // EOF
      read_eval(input)
    }
  }
}
