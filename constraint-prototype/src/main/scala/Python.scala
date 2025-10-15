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
import scala.compiletime.ops.string
import red_pandas.ipython.IPythonKernel

case class PythonEnvironment(kernel: IPythonKernel) extends AutoCloseable {
    def this() = {
      this(IPythonKernel.start())
    }

    def eval(code: String): String = {
      val result = this.kernel.eval(code)
      result
    }
  
    @Override
    def close(): Unit = {
      this.kernel.close()
    }
}

trait PythonEvaluatable {
  def python_representation(): String
}

final case class PythonVariable(python_name: String, env: PythonEnvironment)
    extends PythonEvaluatable {
  override def toString(): String = "#p" ++ this.python_name
  override def python_representation(): String = this.python_name
}

final class PseudoVariable(val name: String) extends Identity {
  override def toString(): String = "#?" ++ this.name

  override def equals(obj: Any): Boolean = obj match {
    case obj: PseudoVariable => this.name == obj.name
    case _                   => false
  }
}

def python_representation(term: Term): Option[String] = term match {
  case term: PythonEvaluatable => Some(term.python_representation())
  case term: String => Some("\"" ++ term ++ "\"")
  case term: Int => Some(term.toString())
}

final case class PythonPredicate(
    args: List[Term],
    template: (List[String]) => String,
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
    val args_repr = this.args.map(python_representation(_))

    if args_repr.forall(_.isDefined) then {
      val python_args = args_repr.flatMap(x => x)
      val code = this.template(python_args.toList)
      val result = this.env.eval(code)
      if result != "True" && result != "False" then {
        println("ERROR: Python predicate returned invalid result: " ++ result)
        println("Code: " ++ code)
      }
      result == "True"
    } else {
      println("WARN: Missing variables for Python predicate " ++ args_repr.filter(!_.isDefined).toString())
      val missing_variables =
        this.args.filter(!_.isInstanceOf[PythonEvaluatable])
      throw IncompleteCall(missing_variables)
    }
}
