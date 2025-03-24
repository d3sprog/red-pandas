:l std.pl

:p exec(open('examples/pandas/python.py').read()); 5

List(?object) :- #p"isinstance($object, list)".
Dict(?object) :- #p"isinstance($object, dict)".
Tuple(?object) :- #p"isinstance($object, tuple)".
Set(?object) :- #p"isinstance($object, set)".
DataFrame(?object) :- #p"isinstance($object, pd.DataFrame)".

get(?object, ?key) :- Dict(?object), #p"$object[$key]".
has_col(?object, ?col) :- DataFrame(?object), #p"$col in $object.columns".

? DataFrame(#px).
? has_col(#px, "id").