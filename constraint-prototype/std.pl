true.

:p import pandas

 String(?object) :- #p"isinstance($object, str)".

List(?object) :- #p"isinstance($object, list)".

Dict(?object) :- #p"isinstance($object, dict)".
Dict.has_key(?object, ?key) :- Dict(?object), #p"$key in $object".
Dict.has_value(?object, ?value) :- Dict(?object), Dict.has_key(?object, ?value), #p"$object.get(key) == $value".
Dict.has_key_value_pair(?object, ?key, ?value) :- Dict(?object), Dict.has_key(?object, ?key), #p"$object[$key] == $value".

Tuple(?object) :- #p"isinstance($object, tuple)".

Set(?object) :- #p"isinstance($object, set)".

% NOTE: we always need to create two predicates for the same function
%       one is for the python version, the one use when the variables
%       are all python variables
%       the second one is the prolog version, simply tagging the variables

pandas.Index(?object) :- #p"isinstance($object, pandas.Index)".
pandas.Index.name(?object, ?name) :- pandas.Index(?object), #p"$object.name".
pandas.Index.unique(?object) :- pandas.Index(?object), #p"$object.unique()".

pandas.Series(?object) :- #p"isinstance($object, pandas.Series)".
pandas.Series.name(?object, ?name) :- pandas.Series(?object), #p"$object.name".
pandas.Series.index(?object, ?out) :- pandas.Series(?object), pandas.Index(?out), #p"$object.index".
pandas.Series.unique(?object) :- pandas.Series(?object), #p"$object.unique()".
pandas.Series.type(?object, ?out) :- pandas.Series(?object), #p"$object.type".

pandas.rename(?object, ?param, ?out) :- DataFrame(?object),
                                        Dict(?param),
                                        foreach(Dict.has_key(?param, ?key),
                                                ),

pandas.DataFrame(?object) :- #p"isinstance($object, pandas.DataFrame)".
pandas.DataFrame.column_names(?object, ?out) :- DataFrame(?object), #p"$object.column_names()".
pandas.DataFrame.has_column(?object, ?col) :- DataFrame(?object), #p"$col in $object.column_names()".
pandas.DataFrame.has_column(?object, ?col) :- DataFrame(?object).

get(?object, ?key) :- Dict(?object), #p"$object[$key]".

index(?object, ?out) :- DataFrame(?object), Ind
