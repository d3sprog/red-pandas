module Parser
    ( expr
    , typeConstant
    ) where

import Control.Arrow
import Control.Monad.Combinators.Expr
import Control.Monad.Except
import Control.Monad.State
import Data.List
import Data.Maybe
import Text.Megaparsec

import Lexer
import Types

rawStruct :: Parser e -> Parser (String, [(Var, e)])
rawStruct e = (,) <$> choice (map symbol reservedStructs) <*> parens (field `sepBy` comma) <?> "struct"
  where
    field = (,) <$> identifier <* reservedOp "=" <*> e <?> "field"

-- TODO: report the location at the beginning (rather than at the end)
struct :: Parser e -> Parser (RPTypeBase e)
struct e = rawStruct e >>= validate
  where
    extract key [] = throwError $ "missing field " ++ key
    extract key (p@(k, v):rest)
        | key == k  = pure (v, rest)
        | otherwise = second (p:) <$> extract key rest

    f key = StateT $ extract key

    -- TODO: might be easier to use permutation parsers from Control.Applicative.Permutations
    -- (from the parser-combinators library) compared to parse anything + validate.
    build "Index"     = TyIndex     <$> (RPIndex     <$> f "name" <*> f "unique")
    build "Series"    = TySeries    <$> (RPSeries    <$> f "name" <*> f "index" <*> f "unique" <*> f "type")
    build "DataFrame" = TyDataFrame <$> (RPDataFrame <$> f "name" <*> f "col_index" <*> f "row_index" <*> f "col_info")
    build "ILoc"      = TyLocInfo   <$> (RPLocInfo   <$> f "source" <*> pure True)
    build "Loc"       = TyLocInfo   <$> (RPLocInfo   <$> f "source" <*> pure False)
    build "GroupBy"   = TyGroupBy   <$> (RPGroupBy   <$> f "source" <*> f "keys")
    build _           = error "struct: unknown struct type"

    validate (name, fields) =
        case runStateT (build name) fields of
            Right (s, rest)
                | null rest -> pure s
                | otherwise -> fail $ "extraneous field(s) " ++ intercalate "," (map fst rest)
            Left msg        -> fail msg

list :: Parser e -> Parser (RPTypeBase e)
list e = analyze <$> brackets (item `sepBy` comma)
  where
    item = Just <$> e <|> Nothing <$ symbol "*"

    analyze raw
        | length raw == length items = TyList (Exact items)
        | otherwise                  = TyList (Partial items)
      where
        items = catMaybes raw

dict :: Parser e -> Parser (RPTypeBase e)
dict e = analyze <$> braces (item `sepBy` comma)
  where
    item = Just <$> ((,) <$> e <* reservedOp ":" <*> e) <|> Nothing <$ symbol "*"

    analyze raw
        | length raw == length items = TyDict (Exact items)
        | otherwise                  = TyDict (Partial items)
      where
        items = catMaybes raw

number :: Parser (RPTypeBase e)
number = TyFloat . Known <$> try float <|> TyInt . Known <$> integer

string :: Parser (RPTypeBase e)
string = TyStr . Known <$> stringLiteral

typeBase :: Parser e -> Parser (RPTypeBase e)
typeBase e =  struct e
          <|> list e
          <|> dict e
          <|> number
          <|> string
          -- TODO: more

factor :: Parser RPExpr
factor =  parens expr
      <|> Var <$> identifier
      <|> If <$> (reserved "if" *> expr) <* reserved "then" <*> expr <* reserved "else" <*> expr
      <|> Let <$> (reserved "let" *> identifier) <* reservedOp "=" <*> expr <* semi <*> expr
      <|> TypeExpr <$> typeBase expr

exprSpecial :: Parser RPExpr
exprSpecial = factor >>= go
  where
    -- Because the attribute isn't an expression, we cannot put the dot
    -- operator into makeExprParser. This code is just a simplified version
    -- of the first step of makeExprParser, which makes sure that the dot
    -- operator binds tightly.
    go e =  (reservedOp "." *> (identifier <?> "attribute") >>= go . Dot e)
        <|> (brackets expr >>= go . At e)
        <|> pure e

expr :: Parser RPExpr
expr = makeExprParser exprSpecial
    [ [InfixN (In <$ reserved "in")]
    , [InfixN (EqComp <$ reservedOp "=="), InfixN (NeqComp <$ reservedOp "!=")]
    , [Prefix (Not <$ reserved "not")]
    , [InfixR (And <$ reserved "and")]
    , [InfixR (Or <$ reserved "or")]
    ] <?> "expression"

typeConstant :: Parser RPType
typeConstant = RPT <$> typeBase typeConstant
