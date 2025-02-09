package red_pandas

type Substitution = Map[Object, Object]

class TypeError(message: String) extends Exception(message) {}

trait Unifiable {
  def unify_self(
      other: Object,
      substitution: Substitution
  ): Option[Substitution]
  def substitute_self(substitution: Substitution): Object
}

def unify(
    a: Object,
    b: Object,
    substitution: Substitution
): Option[Substitution] = {
  if (a == b) Some(substitution)
  else
    val subst = a match {
      case a: Unifiable => a.unify_self(b, substitution)
      case _ =>
        b match {
          case b: Unifiable => b.unify_self(a, substitution)
          case _            => {
            None
          }
        }
    }
    subst
}

def substitute(v: Object, substitution: Substitution): Object = {
  v match {
    case v: Unifiable => v.substitute_self(substitution)
    case _            => v
  }
}

class Variable(name: String) extends Unifiable {
  def unify_self(
      other: Object,
      substitution: Substitution
  ): Option[Substitution] = {
    substitution.get(this) match {
      case Some(value) => unify(value, other, substitution)
      case None =>
        substitution.get(other) match {
          case Some(value) => unify(this, value, substitution)
          case None        => Some(substitution + (this -> other))
        }
    }
  }
  def substitute_self(substitution: Substitution): Object = {
    substitution.get(this) match {
      case Some(value) => value
      case None        => this
    }
  }

  override def toString(): String = "?" ++ name
}

class Predicate(val name: String, val variables: List[Object])
    extends Unifiable {
  def unify_self(
      other: Object,
      substitution: Substitution
  ): Option[Substitution] = {
    other match {
      case other: Predicate =>
        if (name != other.name || variables.length != other.variables.length)
          None
        else
          variables
            .zip(other.variables)
            .foldLeft[Option[Substitution]](
              Some(substitution)
            )((substitution, pair) =>
              substitution match {
                case None               => None
                case Some(substitution) => unify(pair._1, pair._2, substitution)
              }
            )
      case _ => None
    }
  }
  def substitute_self(substitution: Substitution): Object = {
    Predicate(name, variables.map(substitute(_, substitution)))
  }

  override def toString(): String = name ++ "(" ++ variables.mkString(", ") ++ ")"
  override def equals(that: Any): Boolean = {
    that match {
      case that: Predicate =>
        name == that.name && variables == that.variables
      case _ => false
    }
  }
}

class Fail(message: String) extends Unifiable {
  def unify_self(
      other: Object,
      substitution: Substitution
  ): Option[Substitution] = {
    throw new TypeError(message)
  }
  def substitute_self(substitution: Substitution): Object = {
    assert(false, "Fail should not be substituted")
  }
}

class Bail extends Unifiable {
  def unify_self(
      other: Object,
      substitution: Substitution
  ): Option[Substitution] = {
    None
  }
  def substitute_self(substitution: Substitution): Object = {
    assert(false, "Bail should not be substituted")
  }
}

class Succeed extends Unifiable {
  def unify_self(
      other: Object,
      substitution: Substitution
  ): Option[Substitution] = {
    Some(substitution)
  }
  def substitute_self(substitution: Substitution): Object = {
    this
  }
}
