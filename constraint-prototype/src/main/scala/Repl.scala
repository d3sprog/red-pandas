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

class Repl {
  val env = new PythonEnvironment()
  var rules = List.empty[Rule]
  val parser = new Parser(env)

  def eval_query(input: String): Unit = {
    val parsed = parser.parseAll(parser.query, input)
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
    val parsed = parser.parseAll(parser.clause, input)
    print_if_fail(parser, parsed)
    if (parsed.successful) {
      this.rules = parsed.get :: this.rules
    } else {
      println("Failed to parse rule")
    }
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
      val source = scala.io.Source.fromFile(filename)
      val lines = source.getLines()
      for (line <- lines) {
        println(line)
        read_eval(line)
      }
      source.close()
    } else if (input.startsWith(":python") || input.startsWith(":p")) {
      val code = input.split(" ").tail.mkString(" ")
      println(env.eval(code))
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
      read_eval(input)
    }
  }
}
