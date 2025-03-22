package red_pandas

import scala.util.parsing.combinator._

class Parser(env: PythonEnvironment) extends RegexParsers {
    var scope: Map[String, Variable] = Map.empty

    def program = rep(clause) ~ query | query
    def clause: Parser[Clause] = 
        predicate ~ ":-" ~ repsep(predicate, ",") ~ "." ^^ {
            case head ~ _ ~ body ~ _ => (head :: body)
        } |
        predicate ~ "." ^^ { case p ~ _ => List(p)} 
        
    def predicate: Parser[Predicate] = 
        atom ~ "(" ~ repsep(term, ",") ~ ")" ^^ {
            case id ~ _ ~ args ~ _ => Predicate(id, args)
        } |
        atom ^^ {Predicate(_, List.empty)}
    def python_predicate: Parser[PythonPredicate] = 
        "#p" ~ string ^^ { case _ ~ s => python_pred_from_string(s, env) }
    def python_variable: Parser[PythonVariable] = 
        "#p" ~ variable_stem ^^ { case _ ~ s => python_var_from_string(s, env) }
    def pseudo_variable: Parser[PseudoVariable] = 
        "#?" ~ variable_stem ^^ { case _ ~ s => PseudoVariable(s) }
    def term: Parser[Term] = numeral | atom | variable // maybe? | structure
    def query: Parser[Clause] = "?" ~ repsep(predicate, ",") ~ "." ^^ {
        case _ ~ predicates ~ _ => predicates
    }
    def atom: Parser[String] = small_atom | string
    def small_atom: Parser[String] = """[A-Za-z][a-zA-Z0-9_]*""".r ^^ { _.toString }
    def variable: Parser[Variable] = "?" ~ variable_stem ^^ { case _ ~ s => scope.get(s) match {
        case Some(v) => v
        case None => val v = Variable(s); scope = scope + (s -> v); v
    } }

    def variable_stem: Parser[String] = """[a-zA-Z0-9_]*""".r ^^ { _.toString }

    def numeral: Parser[Int] = "[0-9]+".r ^^ { _.toInt }
    def string: Parser[String] = "\"" ~ "[^\"]*".r ~ "\"" ^^ { case _ ~ s ~ _ => s }
}