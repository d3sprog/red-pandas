package red_pandas

import scala.collection.IndexedSeqView.Id

type Term = Any

opaque type Substitution = Map[Variable, Term]
object Substitution {
  def empty: Substitution = Map.empty
  def singleton(variable: Variable, term: Term): Substitution = Map(
    variable -> term
  )

  extension (s: Substitution)
    def get(v: Variable): Option[Term] = s.get(v)
    def put(v: Variable, t: Term): Substitution = s + (v -> t)
    def getOrElse(v: Variable, default: Term): Term = s.getOrElse(v, default)
    def keys: Iterable[Variable] = s.keys
}

extension (it: Iterable[(Variable, Term)]) def toSubst: Substitution = it.toMap

class TypeError(message: String) extends Exception(message) {}

trait Unifiable {
  def unify_with(
      other: Term,
      substitution: Substitution
  ): Option[Substitution]
}

trait Substitutable {
  def substitute_self(substitution: Substitution): Term
}

trait Foreign {
  def call(): Boolean
}

trait Identity {
  def identityEquality(other: Term): Boolean = {
    System.identityHashCode(this) == System.identityHashCode(other)
  }
}

// TODO: think of the arguments
case class IncompleteCall(missing: List[Term]) extends Exception

def unify(
    a: Term,
    b: Term,
    substitution: Substitution
): Option[Substitution] = {
  if (a.isInstanceOf[Identity] && a.asInstanceOf[Identity].identityEquality(b)) Some(substitution)
  else if (a == b) Some(substitution)
  else
    val subst = a match {
      case a: Unifiable => a.unify_with(b, substitution)
      case _ =>
        b match {
          case b: Unifiable => b.unify_with(a, substitution)
          case _ => {
            None
          }
        }
    }
    subst
}

def substitute(v: Term, substitution: Substitution): Term = {
  v match {
    case v: Substitutable => v.substitute_self(substitution)
    case _                => v
  }
}

// Note that Variable uses referential equality for unification
final class Variable(val name: String) extends Unifiable, Substitutable, Identity {
  override def unify_with(
      other: Term,
      substitution: Substitution
  ): Option[Substitution] = {
    substitution.get(this) match {
      case Some(value) => unify(value, other, substitution)
      case None =>
        other match {
          case other: Variable =>
            substitution.get(other) match {
              case Some(value) => unify(this, value, substitution)
              case None        => Some(substitution.put(this, other))
            }
          case _ => Some(substitution.put(this, other))
        }
    }
  }

  override def substitute_self(substitution: Substitution): Term = {
    substitution.getOrElse(this, this)
  }

  override def toString(): String = "?" ++ name

  override def equals(obj: Any): Boolean = obj match {
    case obj: Variable => this.name == obj.name
    case _             => false
  }
}

final case class Predicate(name: String, args: List[Term])
    extends Unifiable,
      Substitutable {

  def can_unify(other: Predicate): Boolean =
    name == other.name && this.args.length == other.args.length

  override def unify_with(
      other: Term,
      substitution: Substitution
  ): Option[Substitution] = other match {
    case other: Predicate if can_unify(other) =>
      unify_args(this.args, other.args, substitution)
    case _ => None
  }

  def unify_args(
      xs: List[Term],
      ys: List[Term],
      substitution: Substitution
  ): Option[Substitution] = (xs, ys) match {
    case (Nil, Nil) => Some(substitution)
    case (x :: xs, y :: ys) =>
      unify(x, y, substitution).flatMap(substitution =>
        unify_args(xs, ys, substitution)
      )
    case _ => None
  }

  override def substitute_self(substitution: Substitution): Term = {
    this.copy(args = this.args.map(substitute(_, substitution)))
  }

  override def toString(): String =
    name ++ (if (args.length > 0) then "(" ++ args.mkString(", ") ++ ")" else "")
  override def equals(that: Term): Boolean = {
    that match {
      case that: Predicate =>
        this.name == that.name && this.args == that.args
      case _ => false
    }
  }
}

class Fail(message: String) extends Foreign {
  override def call(): Boolean = {
    throw new TypeError(message)
  }
}