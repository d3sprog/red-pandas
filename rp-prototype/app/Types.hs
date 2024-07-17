-- | Definitions of types used throughout red-pandas, including the
--   expression language in comp-types.
module Types
    ( Var
    , Info(..)
    , Wildcard(..)
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

-- | Types
--
-- As an example, the dictionary @{\'a\':5, \'b\':6}@ would be represented as:
--
-- > TyDict (Exact [(TyStr (Exact "a"), TyInt (Exact 5)), (TyStr (Exact "b"), TyInt (Exact 6))])
data RPType
    = TyAny  -- ^ Unknown type

    | TyStr (Info String)  -- ^ Strings
    | TyInt (Info Integer)  -- ^ Integers (bounded or unbounded)
    | TyFloat (Info Double)  -- ^ Floating point numbers
    | TyBool (Info Bool)  -- ^ Booleans

    | TyList (Wildcard [RPType])  -- ^ Lists or tuples
    -- TODO: might be a good idea to define some ordering on RPType
    -- and use an actual Data.Set type; same for dictionaries
    -- Sets might also just not be necessary
    | TySet (Wildcard [RPType])  -- ^ Sets
    | TyDict (Wildcard [(RPType, RPType)])  -- ^ Dictionaries

    -- TODO: We probably need to support (at least simple) lambdas, not
    -- sure if TyFun is sufficient for all the use cases we might run into
    | TyFun RPType RPType  -- ^ Function types

    -- TODO: As mentioned in the notes, we need to decide which kinds of operations
    -- we want to support; these will be really complex if we want to support
    -- everything
    | TySeries RPType RPType  -- ^ Pandas series, with its name and the element type
    | TyDataFrame (Wildcard [(RPType, RPType)])  -- ^ Pandas data frame, with column "names"
                                                 -- (typically singleton types) and the element types
    | TyGroupBy  -- ^ Pandas groupby object

    -- TODO: (Multi)indices as a separate type?
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

    -- TODO: This works fine-ish but is pretty inelegant,
    -- probably want to parametrize RPType so that we can
    -- replace this with (RPTypeBase RPExpr) or some such.
    --
    -- As it is, we can easily create a dictionary Dict['a':5] but
    -- once variables are involved, e.g. Dict['a':x], we have to do annoying
    -- detours such as Insert ('a',x) Dict[]
    | Const RPType  -- ^ Type constants

    | If RPExpr RPExpr RPExpr  -- ^ If-then-else
    | And RPExpr RPExpr  -- ^ Boolean conjunction
    | Or RPExpr RPExpr  -- ^ Boolean disjunction
    | Not RPExpr  -- ^ Boolean negation

    -- TODO: are there any use cases for other comparison operators?
    | EqComp RPExpr RPExpr  -- ^ Equality comparison
    | NeqComp RPExpr RPExpr  -- ^ Inequality comparison

    -- Collection manipulation, these should also be usable with pandas types,
    -- i.e. checking that a data frame contains a column with particular name
    -- TODO: will need some adjustments if we want to support multiindices
    -- and maybe some convenience operations like unions or such
    | Contains RPExpr RPExpr  -- ^ Membership testing
    | At RPExpr RPExpr  -- ^ Element access
    | Insert RPExpr RPExpr  -- ^ Insertion
    | Delete RPExpr RPExpr  -- ^ Deletion

    -- TODO: Probably some string and numeric operations as well
    deriving (Show)
