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
      this.kernel.eval(code)
    }
  
    @Override
    def close(): Unit = {
      this.kernel.close()
    }
}

trait PythonEvaluatable {
  def python_representation(): String
}

object PythonEvaluatable {
  given intAsPythonEvaluatable: Conversion[Int, PythonEvaluatable] with {
    def apply(i: Int): PythonEvaluatable = new PythonEvaluatable {
      override def python_representation(): String = i.toString
    }
  }

  given stringAsPythonEvaluatable: Conversion[String, PythonEvaluatable] with {
    def apply(s: String): PythonEvaluatable = new PythonEvaluatable {
      override def python_representation(): String = s
    }
  }
}


extension (i: Int){
  def python_representation(): String = i.toString
}
extension (s: String){
  def python_representation(): String = s
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
