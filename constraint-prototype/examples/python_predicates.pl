List(?object) :- #p"$object.type_str == 'list'".
Dict(?object) :- #p"$object.type_str == 'dict'".
Tuple(?object) :- #p"$object.type_str == 'tuple'".
Set(?object) :- #p"$object.type_str == 'set'".

get(?object, ?key) :- Dict(?object), #p"$object[$key]".

:p x = [1, 2, 3]
:p y = {"a": 1, "b": 2}
:p z = (1, 2, 3)
:p w = {1, 2, 3}

? List(#px).
? Dict(#py).
? Tuple(#pz).
? Set(#pw).