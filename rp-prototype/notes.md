# Libraries

* aeson for JSON input/output
* might want to relax the base library bound in `.cabal` file, should work fine with all stable GHC releases

# Types

* `DataFrame`s support is given, but we'll also need to support `Series`
  - Indices?
  - We should keep track of the name (for grouping operations)
* Dictionaries will be necessary (at the very least to somehow express the type of functions with named parameters)
  - Should the key-value pairs be types or expressions? We could use the singleton types to describe them, such as `Dict[String["col1"]: Int[1]]` or just put them in directly, as `Dict["col1": 1]`
  - The latter might be more restrictive, but it doesn't allow mostly useless types such as `Dict[String: String]` (or maybe someone wants to know that a dictionary contains one string key with a corresponding string value, without knowing their precise contents)
    * Though treating everything uniformly as a type has other advantages (esp for the expression language)
  - Something like `Dict<K,V>[*]` *probably* isn't necessary
  - As mentioned, we probably want to distinguish between `Dict[]` and `Dict[*]`, but should we also do the same for `Dict[k1:v1, k2:v2]` vs `Dict[k1:v1, k2:v2, *]`?
* Sets don't seem that much useful for pandas, but maybe there's something
* Any other basic types to support (other than strings, bools and numbers)? datetime?
* How to handle missing data?
* How to handle heterogeneous data?
  - A simple option might be to use some sort of `Any` type (which would roughly be what pandas calls `object`)
* Should we have a separate type for `.loc` and `.iloc` attributes or try to treat `.loc[...]` and `.iloc[...]` as a single entity?
  - If we do the former, what kind of information is required for all `.loc` uses (source (DataFrame/Series), type, columns? - for `.loc[1,['col1','col2']]`)
  - for `.iloc` we still need to keep track of columns (at least their count) so that we can stop an obvious out-of-bounds access
  - Slicing support
  - Series access
  - Source information for destructive updates? (i.e. someone changes the type of a column)
    * Another issue: if there's no `col3` then `df.loc[:,'col3']` is an error but `df.loc[:,'col3'] = 5` isn't
* How to deal with non-unique indices?
  - `df.loc[ix]` can produce either a series or a dataframe
* Should we do anything about:
  - Extension data types
  - Categorical data
* Hierarchical indexing and the MultiIndex type
  - Even if we ignore the row index, the column index can also be hierarchical
  - Massively complicates the types involved in lookup operations
    * `df['col1']` might be another dataframe
  - `stack` and `unstack` operations, `set_index` with a list
* Database-like operations
  - `merge` doesn't seem too bad, just need to be careful when called without any key (merges on overlapping columns then)
  - It does seem that pandas doesn't specify the order of columns in the result, which will probably be a problem (even if we reconstruct the behavior exactly, it could change without any warning in newer versions)
  - handle `left_on` and `right_on` version, similarly for `left_index` and `right_index` (and combinations thereof)
  - automatic renaming for columns which are present in both data frames but aren't join on, `suffixes=` param
  - `concat` is very generic and also very annoying
    * Basic use case seems fairly reasonable though, only gets bad with `keys` creating hierarchical indices or when merging series column-wise
* Grouping
  - `groupby` can also be given a series, not sure if there are any useful use cases that aren't just `df.groupby(df['key'])`
    * if we want to support this, the series type needs to keep track of the series name
  - giving it a list of series or column names creates a hierarchical index
  - `mean` and non-numeric columns (and `numeric_only` param)
  - `size` vs `count`, we don't really have to care about the contents but `count` creates more columns
  - do we want to handle grouping on different axes?
    * possibly also with a dictionary mapping
  - functional parameters, i.e. `groupby(len)`
  - which aggregation functions do we want to support?
    * the standard ones should be simple enough (`any`, `all`, `count`, `cummin`, `cummax`, `cumsum`, `first`, `last`, `mean`, `median`, `min`, `max`, `size`, `sum`, `std`, `var`)
    * series methods? like `nsmallest` (creates hierarchical indices)
    * custom functions?
    * note that the standard agg functions can also be given like `agg('mean')` or `agg(['mean','sum'])` (the latter creates a hierarchical index)
    * renaming with a list of tuples or a dictionary... or a combination thereof `agg({'col1':['mean','sum'], 'col2':'max'})`
  - the `groupby` object supports indexing
    * `df.groupby('col1')['col2']`, basically the same as doing `df['col2'].groupby(df['col1'])` - note that groupby needs to be given a series, as that column no longer exists on the data frame it's called on
    * internal representation of the groupby object type will have to take this into account
  - `pivot_tables` as a combination of `groupby` and reshaping?