package red_pandas

case class PythonEnvironment() {}

def eval_python(code: String, env: PythonEnvironment): Boolean = {
    println("Evaluating $code")
    true
}

trait PythonEvaluatable {
    def python_representation(): String
}

final case class PythonVariable(python_name: String, env: PythonEnvironment) extends PythonEvaluatable {
    override def toString(): String = "python/" ++ this.python_name
    override def python_representation(): String = this.python_name
}

final class PseudoVariable(name: String) {
    override def toString(): String = "pseudo/" ++ this.name
}

final case class PythonPredicate(args: List[Term], template: (List[String]) => String, env: PythonEnvironment) extends Substitutable, Foreign {
  override def substitute_self(substitution: Substitution): Term = {
    this.copy(args = this.args.map(_ match {
        case arg: Variable => transitive_get(substitution, arg)
        case arg => arg
    }))
  }

  override def call(): Boolean = if this.args.forall(_.isInstanceOf[PythonEvaluatable]) then {
    val python_args = this.args.map(_.asInstanceOf[PythonEvaluatable].python_representation())
    val code = this.template(python_args)
    eval_python(code, this.env)
  } else {
    val missing_variables = this.args.filter(!_.isInstanceOf[PythonEvaluatable])
    throw IncompleteCall(missing_variables)
  }
}


