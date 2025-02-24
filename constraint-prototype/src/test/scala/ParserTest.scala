package red_pandas

class ParserTest extends munit.FunSuite {
    test("parse simple clause") {
        val env = new PythonEnvironment()
        val parser = new Parser(env)
        val result = parser.parseAll(parser.clause, "likes(?maya, ?pandas).")
        println("Result: " + result)
        assert(result.successful)
        assertEquals(result.get, List(Predicate("likes", List(Variable("maya"), Variable("pandas")))))
    }

    test("parse clause with body") {
        val env = new PythonEnvironment()
        val parser = new Parser(env)
        val result = parser.parseAll(parser.clause, "likes(?maya, ?pandas) :- cute(?pandas).")
        println("Result: " + result)
        assert(result.successful)
        assertEquals(result.get, List(Predicate("likes", List(Variable("maya"), Variable("pandas"))), Predicate("cute", List(Variable("pandas")))))
    }

    test("parse query") {
        val env = new PythonEnvironment()
        val parser = new Parser(env)
        val result = parser.parseAll(parser.query, "?- likes(?maya, ?pandas).")
        println("Result: " + result)
        assert(result.successful)
        assertEquals(result.get, List(Predicate("likes", List(Variable("maya"), Variable("pandas")))))
    }

    test("parse python predicate") {
        val env = new PythonEnvironment()
        val parser = new Parser(env)
        val result = parser.parseAll(parser.python_predicate, "#p\"print('Hello, world!')\"")
        println("Result: " + result)
        assert(result.successful)
        assert(result.get.isInstanceOf[PythonPredicate])
        assert(result.get.template(List.empty) == "print('Hello, world!')")
    }

    test("parse python variable") {
        val env = new PythonEnvironment()
        val parser = new Parser(env)
        val result = parser.parseAll(parser.python_variable, "#pX")
        println("Result: " + result)
        assert(result.successful)
        assertEquals(result.get, PythonVariable("X", env))
    }

    test("parse pseudo variable") {
        val env = new PythonEnvironment()
        val parser = new Parser(env)
        val result = parser.parseAll(parser.pseudo_variable, "#?X")
        println("Result: " + result)
        assert(result.successful)
        assertEquals(result.get, PseudoVariable("X"))
    }
}