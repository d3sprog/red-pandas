package red_pandas

import scala.util.matching.Regex
import java.io.File
import scala.sys.process._
import java.io.OutputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt 
import scala.concurrent.Await
import scala.util.Success
import scala.util.Failure

case class PythonEnvironment(process: InteractiveProcess) extends AutoCloseable {
    def this() = {
      this(
        InteractiveProcess(
          Seq("python3", "-i"),
          environment = Map("PYTHONUNBUFFERED" -> "1")
        )
      )
      this.process.start() match {
        case Success(value) => println("Python started")
        case Failure(exception) => println("Python failed to start: " + exception.getMessage())
      }
    }

    def eval(code: String): String = {
      Await.result(this.process.sendAndReceive(code), 5.seconds).dropRight(1) // remove the last newline
    }
  
    @Override
    def close(): Unit = {
      this.process.stop()
    }
}

trait PythonEvaluatable {
  def python_representation(): String
}

final case class PythonVariable(python_name: String, env: PythonEnvironment)
    extends PythonEvaluatable {
  override def toString(): String = "python/" ++ this.python_name
  override def python_representation(): String = this.python_name
}

final class PseudoVariable(val name: String) extends Identity {
  override def toString(): String = "pseudo/" ++ this.name

  override def equals(obj: Any): Boolean = obj match {
    case obj: PseudoVariable => this.name == obj.name
    case _                   => false
  }
}

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
    env: PythonEnvironment
): PythonPredicate = {
  val regex = Regex("\\$(\\w+)");
  val variables = regex.findAllIn(string).distinct.toList

  val template = (args: List[PythonEvaluatable]) => {
    var code = string

    for ((variable, i) <- variables.zipWithIndex) {
      code = code.replaceAllLiterally(
        "$" ++ variable,
        args(i).python_representation()
      )
    }
    code
  }

  val free_variables = variables.map(Variable(_));
  PythonPredicate(free_variables, template, env)
}

def python_var_from_string(string: String, env: PythonEnvironment): PythonVariable =
  PythonVariable(string, env)

final case class PythonPredicate(
    args: List[Term],
    template: (List[PythonEvaluatable]) => String,
    env: PythonEnvironment
) extends Substitutable,
      Foreign {

  override def substitute_self(substitution: Substitution): Term = {
    this.copy(args = this.args.map(_ match {
      case arg: Variable => transitive_get(substitution, arg)
      case arg           => arg
    }))
  }

  override def call(): Boolean =
    if this.args.forall(_.isInstanceOf[PythonEvaluatable]) then {
      val python_args = this.args.map(_.asInstanceOf[PythonEvaluatable])
      val code = this.template(python_args)
      this.env.eval(code) == "True"
    } else {
      val missing_variables =
        this.args.filter(!_.isInstanceOf[PythonEvaluatable])
      throw IncompleteCall(missing_variables)
    }
}
