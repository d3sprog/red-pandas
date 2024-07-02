## Background

We have the following types:

* `Dataframe[col1:T1, ..., coln:TN]` for dataframe with known columns `col1..coln` of known types.
* It may be a good idea to distinguish `Dataframe[]` and e.g., `Dataframe[*]`. The former is known to have no columns, whereas the latter has some not yet known columns. Accessing column `col` on `Dataframe[]` is a type error, but accessing it on `Dataframe[*]` is fine. 
* Actually, we also need to keep track of indices (and pandas supports multi-indices too). I'm not sure what the best way of doing this is.

To support type checking of pandas APIs, we may need some peculiar types for primitive Python data structures:

* `Dict[key1:T1, ..., keyn:TN]` for Python dictionaries created using `{k1:v1, k2:v2}`
* `[T1, ..., TN]` for a list containing `N` values of types `T1`..`TN` (more like a tuple, but we need the static information in some cases)

## Walkthrough

### Reading data

```python
import pandas as pd
raw = pd.read_csv("rooms.csv")
```


Read data from CSV. The type of `row` is `Dataframe[*]` before we run this code. After we run it, the type becomes `Dataframe['room':string, ' floor':int, ' capacity':int]` (yes, there are spaces at the start of some of the names because `read_csv` does not automatically trim the names).

```python
rooms = raw.rename({" floor":"floor"," capacity":"capacity"}, axis="columns")
```

After we run `read_csv`, type of `rooms` will be `Dataframe['room':string, 'floor':int, 'capacity':int]` (before, it is just `Dataframe[*]`).

We probably need to infer the type of the argument as `Dict[' floor':'floor', ' capacity','capactiy']` (treating the keys as names and their types as a string literal type) Alternatively, we could just treat the whole value as a literal type and avoid the need for `Dict` types (but they may be needed elsewhere).

```python
rooms[["room","capacity"]]
```

Before `read_csv`, this is allowed because `Dataframe[*]` allows any access. After `read_csv`, this checks that the given columns exist. Again, we need some kind of literal type for the collection.

### Grouping

```python
grouped = rooms.groupby(rooms["floor"]).agg({"capacity":"sum","room":"count"})
grouped.head()
```

We can check that columns `floor`, `capacity` and `room` exist. The resulting dataframe has a type `Dataframe['floor':int, 'capacity':int, 'room':int]`. Note that the type of `room` changed from `string` to `int`!

The type of the object returned by `groupby` needs to keep all the information about the input dataframe and the specified grouping key. Note that `rooms["floor"]` returns a series and we need to keep track of the name of the series:


```python
rooms.groupby(rooms["floor"].rename("etage")).agg({"capacity":"sum","room":"count"})
```

If we rename the series using `rename`, the resulting dataframe will have a column `etage` instead of `floor`.

```python
grouped = rooms.groupby(rooms["floor"]).agg(capacity=("capacity","sum"),count=("room","count"))
grouped.head()
```

This is another way of calling `agg`. This time, we specify the new column names as named parameters and their value as a tuple of original column name and aggregation operation (from a predefined fixed set).

### Indices


```python
indexed = rooms.set_index("room")
```

This creates a dataframe with `room` column as the index. We can use this to index into the data:

```python
indexed.loc["S5","capacity"]
```

The first parameter is value of the same type as is the type of the index column (`room` of type `string`). The second parameter is a column name. Calling `rooms.["S5","capacity"]` would be an error, because `rooms` has a numerical index.


### Melting and pivoting

```python
melted = rooms.melt(id_vars=["room"],var_name="feature")
melted.head()
```

This turns columns that are not in `id_vars` into features (if there are $k$ such columns, the resulting frame will have $k*rows(df)$ rows). The resulting dataframe will keep all the columns in `id_vars`, add `feature` column (of type string) and a new column `value`.


```python
pivoted = melted.pivot(index="room",columns="feature",values="value")
pivoted.head()
```

Pivot is an inverse oepration. The values from the `feature` column become new columns. We cannot statically check this if we do not know the data.


```python
pivoted["capacity"]
```

This is allowed because `pivoted` has type `Dataframe[*]`. After running `pivot`, it will include a column `'capacity':int` (and after running pivot, `pivoted["somethingbad"]` should be an error).