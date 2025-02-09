package red_pandas

val rules: List[Rule] = List(
  List(Predicate("father", List("John", "Mary"))),
  List(Predicate("father", List("John", "Alice"))),
  List(Predicate("mother", List("Susan", "Alice"))),
  List(Predicate("mother", List("Susan", "Mary"))), {
    val x = new Variable("X")
    val y = new Variable("Y")
    List(
      Predicate("parent", List(x, y)),
      Predicate("father", List(x, y))
    )
  }, {
    val x = new Variable("X")
    val y = new Variable("Y")
    List(
      Predicate("parent", List(x, y)),
      Predicate("mother", List(x, y))
    )
  }
)

class ResolutionTest extends munit.FunSuite {
  test("father(X, Y)") {
    val expected = List(
      List(Predicate("father", List("John", "Alice"))),
      List(Predicate("father", List("John", "Mary")))
    )
    val goal =
      List(Predicate("father", List(new Variable("X"), new Variable("Y"))))
    assertEquals(resolve_pretty(goal, rules), expected)
  }

  test("mother(X, Y)") {
    val expected = List(
      List(Predicate("mother", List("Susan", "Mary"))),
      List(Predicate("mother", List("Susan", "Alice"))),
    )
    val goal =
      List(Predicate("mother", List(new Variable("X"), new Variable("Y"))))
    assertEquals(resolve_pretty(goal, rules), expected)
  }

  test("mother(X, Mary)") {
    val expected = List(
      List(Predicate("mother", List("Susan", "Mary")))
    )
    val goal =
      List(Predicate("mother", List(new Variable("X"), "Mary"))
    )
    assertEquals(resolve_pretty(goal, rules), expected)
  }

  test("fail") {
    val goal = List(Fail("test"))
    try {
      resolve_pretty(goal, rules)
      fail("Expected exception")
    } catch {
      case e: TypeError => assertEquals(e.getMessage, "test")
    }
  }
}
