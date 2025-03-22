father("John", "Mary").
father("John", "Alice").
mother("Susan", "Mary").
mother("Susan", "Alice").
parent(?X, ?Y) :- father(?X, ?Y).
parent(?X, ?Y) :- mother(?X, ?Y).

? father(?x, ?y).
? mother(?x, ?y).
? mother(?x, "Mary").
? parent(?x, ?y).