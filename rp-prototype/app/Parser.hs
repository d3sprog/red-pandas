module Parser where

import Control.Monad.Combinators.Expr
import Text.Megaparsec

import Lexer
import Types

-- TODO: barebones definition for testing precedence
factor :: Parser RPExpr
factor =  parens expr
      <|> Var <$> identifier
      <?> "variable"

factorDot :: Parser RPExpr
factorDot = factor >>= go
  where
    -- Because the attribute isn't an expression, we cannot put the dot
    -- operator into makeExprParser. This code is just a simplified version
    -- of the first step of makeExprParser, which makes sure that the dot
    -- operator binds tightly.
    go e = (reservedOp "." *> (identifier <?> "attribute") >>= go . Dot e) <|> pure e

expr :: Parser RPExpr
expr = makeExprParser factorDot
    [ [InfixN (In <$ reserved "in")]
    , [InfixN (EqComp <$ reservedOp "=="), InfixN (NeqComp <$ reservedOp "!=")]
    ]
