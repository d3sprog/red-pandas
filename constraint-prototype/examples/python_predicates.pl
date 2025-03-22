List(?object) :- #p"isinstance($object, list)".
Dict(?object) :- #p"isinstance($object, dict)".
Tuple(?object) :- #p"isinstance($object, tuple)".
Set(?object) :- #p"isinstance($object, set)".

get(?object, ?key) :- Dict(?object), #p"$object[$key]".

:p x = [1, 2, 3]
:p y = {"a": 1, "b": 2}
:p z = (1, 2, 3)
:p w = {1, 2, 3}

? List(#px).
? Dict(#py).
? Tuple(#pz).
? Set(#pw).