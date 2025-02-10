package red_pandas

type Clause = List[Any]
type Rule = Clause

case class Goal(clause: Clause, substitution: Substitution) {
  def is_resolved: Boolean = clause.isEmpty

  def get_substitution: Substitution = {
    assert(is_resolved)
    substitution
  }

  def resolve(rules: List[Clause]): List[Goal] = {
    this.clause match {
      case Nil => List(Goal(List.empty, this.substitution))
      case head :: tail =>
        head match {
          case head: Boolean => if head then List(Goal(tail, this.substitution)) else List.empty
          case head: Foreign => if head.call() then List(Goal(tail, this.substitution)) else List.empty
          case head =>
            rules.flatMap(rule => {
              unify(head, rule.head, substitution)
                .map(subst =>
                  Goal(rule.tail.map(substitute(_, subst)) ++ tail, subst)
                )
            })
        }
    }
  }
}

def transitive_get(
    substitution: Substitution,
    variable: Variable
): Term = {
  substitution.get(variable) match {
    case Some(value) =>
      value match {
        case v: Variable => transitive_get(substitution, v)
        case _           => value
      }
    case None => variable
  }
}

def cleanup_substitution(substitution: Substitution): Substitution = {
  substitution.keys.map(key => (key, transitive_get(substitution, key))).toSubst
}

def iteration_limit = 1000

def resolve_goals_collect_incomplete(
    innitial_goal: Clause,
    rules: List[Rule]
): (List[Substitution], Map[List[Term], Goal]) = {
  var stack = List(new Goal(innitial_goal, Substitution.empty))
  var results = List.empty[Substitution]
  var iteration = 0
  var incomplete: Map[List[Term], Goal] = Map.empty

  while (stack.nonEmpty) {
    val goal = stack.head
    stack = stack.tail

    if (iteration >= iteration_limit) {
      throw new Exception("Iteration limit reached")
    }
    iteration += 1

    if (goal.is_resolved) {
      results = cleanup_substitution(goal.get_substitution) :: results
    } else {
      try {
        val new_goals = goal.resolve(rules)
        stack = new_goals ++ stack
      } catch {
        case e: IncompleteCall =>
          incomplete = incomplete + (e.missing -> goal)
      }
    }
  }

  (results, incomplete)
}

def resolve_goals(
    goal: Clause,
    rules: List[Rule]
): List[Substitution] = {
  resolve_goals_collect_incomplete(goal, rules)._1
}

def resolve_pretty(
    goal: Clause,
    rules: List[Rule]
) = {
  val results = resolve_goals(goal, rules)
  val cleaned =
    results.map(substitution => goal.map(substitute(_, substitution)))
  cleaned
}
