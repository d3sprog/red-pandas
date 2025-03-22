package red_pandas

import scala.util.parsing.combinator._

class Parser(env: PythonEnvironment) extends RegexParsers {
/// parses a python code that contains `$variable`
/// for variables that should be substituted by
/// the solver
///
/// Some invariants must hold:
/// - variables must only contain [a-zA-Z]
/// - variables must be unique
/// - order of first appearance is the order of the arguments
  def python_pred_from_string(
      string: String,
      env: PythonEnvironment,
  ): PythonPredicate = {
    val regex = """\$(\??[a-zA-Z0-9_]*)""".r
    val variables = regex
      .findAllMatchIn(string)
      .map(_.group(1))
      .distinct
      .map(variable =>
        scope.get(variable) match {
          case Some(v) => v
          case None => {
            val v = Variable(variable)
            scope = scope + (variable -> v)
            v
          }
        }
      )
      .toList

    val template = (args: List[PythonEvaluatable]) => {
      var code = string

      if (variables.length != args.length) {
        println("ERROR: Mismatched number of variables and arguments")
        println("Predicate: " ++ string)
        println("Variables: " ++ variables.toString())
        println("Arguments: " ++ args.toString())
      }

      for ((variable, i) <- variables.zipWithIndex) {
        code = code.replaceAllLiterally(
          "$" ++ variable.name,
          args(i).python_representation()
        )
      }
      code
    }

    PythonPredicate(variables, template, env)
  }

  var scope: Map[String, Variable] = Map.empty

  def program = rep(clause) ~ query | query
  def clause: Parser[Clause] =
    (predicate | python_predicate) ~ ":-" ~ repsep(
      predicate | python_predicate,
      ","
    ) ~ "." ^^ {
      case head ~ _ ~ body ~ _ => (head :: body)
    } |
      (predicate | python_predicate) ~ "." ^^ { case p ~ _ => List(p) }

  def predicate: Parser[Predicate] =
    atom ~ "(" ~ repsep(term, ",") ~ ")" ^^ { case id ~ _ ~ args ~ _ =>
      Predicate(id, args)
    } |
      atom ^^ { Predicate(_, List.empty) }
  def python_predicate: Parser[PythonPredicate] =
    "#p" ~ string ^^ {
      case _ ~ s => this.python_pred_from_string(s, env)
    }
  def python_variable: Parser[PythonVariable] =
    "#p" ~ variable_stem ^^ { case _ ~ s => python_var_from_string(s, env) }
  def pseudo_variable: Parser[PseudoVariable] =
    "#?" ~ variable_stem ^^ { case _ ~ s => PseudoVariable(s) }
  def term: Parser[Term] =
    numeral | atom | variable | python_variable | pseudo_variable // maybe? | structure
  def query: Parser[Clause] = "?" ~ repsep(predicate, ",") ~ "." ^^ {
    case _ ~ predicates ~ _ => predicates
  }
  def atom: Parser[String] = small_atom | string
  def small_atom: Parser[String] = """[A-Za-z][a-zA-Z0-9_]*""".r ^^ {
    _.toString
  }
  def variable: Parser[Variable] = "?" ~ variable_stem ^^ { case _ ~ s =>
    scope.get(s) match {
      case Some(v) => v
      case None    => val v = Variable(s); scope = scope + (s -> v); v
    }
  }

  def variable_stem: Parser[String] = """[a-zA-Z0-9_]*""".r ^^ { _.toString }

  def numeral: Parser[Int] = "[0-9]+".r ^^ { _.toInt }
  def string: Parser[String] = "\"" ~ "[^\"]*".r ~ "\"" ^^ { case _ ~ s ~ _ =>
    s
  }
}
