package red_pandas

type Clause = List[Any]
type Rule = Clause

class Goal (clause: Clause, substitution: Substitution) {
  def is_resolved: Boolean = clause.isEmpty

  def get_substitution: Substitution = {
    assert(is_resolved)
    substitution
  }

  def resolve(rules: List[Clause]): List[Goal] = {
    if (clause.isEmpty) List(new Goal(List.empty, this.substitution))
    else {
      val head = clause.head
      val tail = clause.tail

      val new_goals: List[Goal] = rules
        .flatMap(rule =>
          val rule_head = rule.head
          val rule_body = rule.tail
            unify(head, rule_head, substitution) match {
              case Some(sub) =>
                Some(
                  new Goal(
                    rule_body.map(substitute(_, sub)) ++ tail,
                    sub
                  )
                )
              case None => None
          }
        )
      new_goals
    }
  }
}

def transitive_get(
    substitution: Substitution,
    variable: Any
): Any = {
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
  substitution.keys.map(key => (key, transitive_get(substitution, key))).toMap
}

def iteration_limit = 1000

def resolve_goals(
    innitial_goal: Clause,
    rules: List[Rule],
): List[Substitution] = {
  var stack =List(new Goal(innitial_goal, Map.empty))
  var results = List.empty[Substitution]
  var iteration = 0

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
      val new_goals = goal.resolve(rules)
      stack = new_goals ++ stack
    }
  }

  results
}

def resolve_pretty(
  goal: Clause,
  rules: List[Rule],
) = {
  val results = resolve_goals(goal, rules)
  val cleaned = results.map(substitution => goal.map(substitute(_, substitution)))
  cleaned
}
