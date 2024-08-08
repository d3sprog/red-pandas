-- | Definitions of types used throughout red-pandas, including the
--   expression language in comp-types.
module Types
    ( Var
    , Info(..)
    , Wildcard(..)
    , RPIndex(..)
    , RPSeries(..)
    , RPDataFrame(..)
    , RPLocInfo(..)
    , RPGroupBy(..)
    , RPTypeBase(..)
    , RPType(..)
    , RPCompType(..)
    , RPExpr(..)
    ) where

type Var = String

-- | Tags for types that don't support partial information
data Info a
    = Known a
    | Unknown
    deriving (Show)

-- | Tags for collection types that support partial information
data Wildcard a
    = Exact a  -- ^ The collection contains exactly the given values
    | Partial a  -- ^ The collection contains at least the given values
    deriving (Show)

-- TODO: Type of index elements seem far less useful, but maybe should be
-- tracked as well?

-- | Pandas indices
data RPIndex t
    = RPIndex
    { ixName :: t  -- ^ Index name
    , ixUnique :: t  -- ^ Are the values unique?
    }
    deriving (Show)

-- | Pandas series
data RPSeries t
    = RPSeries
    { srName :: t  -- ^ Series name
    , srIndex :: t  -- ^ Series index
    , srUnique :: t  -- ^ Are the values unique?
    , srType :: t  -- ^ Type of the elements
    }
    deriving (Show)

-- | Pandas data frame
data RPDataFrame t
    = RPDataFrame
    { dfName :: t  -- ^ Data frame name
    , dfRowIndex :: t  -- ^ Row index
    , dfColIndex :: t  -- ^ Column index
    , dfColInfo :: t  -- ^ Names and types of columns
    }
    deriving (Show)

-- TODO: __dict__ gives an empty result on the loc/iloc objects, but according to the
-- source code for the base class (https://github.com/pandas-dev/pandas/blob/main/pandas/_libs/indexing.pyi),
-- the only relevant information is the "name" (in our case loc/iloc) and the source object.

-- | Pandas indexer
data RPLocInfo t
    = RPLocInfo
    { liSource :: t  -- ^ Source object
    , liNumeric :: Bool  -- ^ Is this an iloc indexer?
    }
    deriving (Show)

-- TODO: The pandas groupby object contains (at least) the following:
--
-- >>> df.groupby('a').__dict__
-- {'_selection': None, 'level': None, 'as_index': True, 'keys': 'a', 'sort': True,
-- 'group_keys': True, 'dropna': True, 'observed': False, 'obj': <dataframe>, 'axis': 0,
-- '_grouper': <basegrouper>, 'exclusions': frozenset({'a'})}
--
-- We'll need to pick a reasonable subset of these to be able to type check common uses.

-- | Pandas GroupBy object
data RPGroupBy t
    = RPGroupBy
    { gbSource :: t  -- ^ Source object
    , gbKeys :: t  -- ^ Keys to group on
    }
    deriving (Show)

-- TODO: Do we want to track more precise information about the slices?

-- | Base, non-recursive type for red-pandas types.
--
-- Parametrized by the type of subterms.
data RPTypeBase t
    = TyAny  -- ^ Unknown type

    | TyStr (Info String)  -- ^ Strings
    | TyInt (Info Integer)  -- ^ Integers (bounded or unbounded)
    | TyFloat (Info Double)  -- ^ Floating point numbers
    | TyBool (Info Bool)  -- ^ Booleans

    | TySlice  -- ^ Slice object
    | TyList (Wildcard [t])  -- ^ Lists or tuples
    | TyDict (Wildcard [(t, t)])  -- ^ Dictionaries

    -- TODO: We probably need to support (at least simple) lambdas, not
    -- sure if TyFun is sufficient for all the use cases we might run into
    | TyFun t t  -- ^ Function types

    | TyIndex (RPIndex t)  -- ^ Indices
    | TySeries (RPSeries t)  -- ^ Series
    | TyDataFrame (RPDataFrame t)  -- ^ Data frames
    | TyLocInfo (RPLocInfo t)  -- ^ Indexers
    | TyGroupBy (RPGroupBy t)  -- ^ GroupBy objects
    deriving (Show)

newtype RPType = RPT { unRPT :: RPTypeBase RPType }
    deriving (Show)

-- Much like in the paper, comp types will likely be treated separately
-- | Comp types
data RPCompType
    = TyComp Var (RPType, RPExpr) (RPType, RPExpr)
    deriving (Show)

-- TODO: Mutation shouldn't really be necessary in comp types, so we could
-- keep the description language purely functional
-- | Comp-type expressions which evaluate to 'RPType'.
data RPExpr
    = Var Var  -- ^ Variables
    | Let Var RPExpr RPExpr  -- ^ Let expression
    | TypeExpr (RPTypeBase RPExpr)  -- ^ Type expressions

    | If RPExpr RPExpr RPExpr  -- ^ If-then-else
    | And RPExpr RPExpr  -- ^ Boolean conjunction
    | Or RPExpr RPExpr  -- ^ Boolean disjunction
    | Not RPExpr  -- ^ Boolean negation

    -- TODO: maybe allow dynamic construction of the error message?
    | Fail String  -- ^ Failure

    -- TODO: are there any use cases for other comparison operators?
    | EqComp RPExpr RPExpr  -- ^ Equality comparison
    | NeqComp RPExpr RPExpr  -- ^ Inequality comparison

    | Dot RPExpr String  -- ^ Attribute access

    -- Collection manipulation, these should also be usable with pandas types,
    -- i.e. checking that a data frame contains a column with particular name
    -- TODO: will need some adjustments if we want to support multiindices
    -- and maybe some convenience operations like unions or such
    | In RPExpr RPExpr  -- ^ Membership testing
    | At RPExpr RPExpr  -- ^ Element access
    | Insert RPExpr RPExpr  -- ^ Insertion
    | Delete RPExpr RPExpr  -- ^ Deletion

    -- TODO: Probably some string and numeric operations as well
    deriving (Show)

-- Here's how a simple, single-dimensional element access comp type might
-- look like (in yet to be defined language for the above).
--
-- ix <: Any / Any ->  # If an object requires a particular index type,
--                     # we could check for it here. Not super useful for
--                     # series/dataframes as the names can be anything hashable.
-- (
--   if (tself.type_str == "series" or tself.type_str == "data_frame") and ix.type_str in ["dict", "loc_info", "group_by"] then
--     fail "can't index with that"  # Series and data frames don't allow these as indices
--   else if tself.type_str == "series" then
--     if tself.index.unique and ix.is_simple then
--       tself.type
--     else  # Access into non-unique series can return either a scalar or another series.
--       Any
--   else if tself.type_str == "dataframe" then
--     if tself.col_index.unique and ix.is_simple and ix.is_known then  # Unique column names and a known value as an index
--       if ix in tself.col_info then  # Known column, return a series with that type
--         Series(name=ix, index=tself.row_index, unique=Bool, type=tself.col_info[ix])
--       else if tself.col_info.is_exact then  # Column wasn't found and column info has exact information
--         fail "column doesn't exist"
--       else
--         Any
--     else  # Optionally, handle slices, indices, series and data frames here
--       Any
--   else  # Optionally, handle lists and dictionaries here
--     Any
-- ) / Any
