module Lexer
    ( Parser
    , lexeme
    , symbol
    , parens
    , braces
    , brackets
    , semi
    , comma
    , integer
    , float
    , identifier
    , reservedKeywords
    , reservedStructs
    , reserved
    , reservedOp
    , stringLiteral
    ) where

import Data.Void
import Text.Megaparsec
import Text.Megaparsec.Char
import Text.Megaparsec.Char.Lexer qualified as L

-- TODO: Do we want any fancier error reporting here?
-- | Type of string parsers without any custom error components.
type Parser a = Parsec Void String a

-- TODO: multiline comments?
-- | Parse whitespace (including comments) and throw away the result.
sc :: Parser ()
sc = L.space space1 (L.skipLineComment "#") empty

-- | Create a lexeme parser.
--
-- Automatically skips whitespace (see 'sc') after running the given parser.
lexeme :: Parser a -> Parser a
lexeme = L.lexeme sc

-- | Parse the given string.
symbol :: String -> Parser String
symbol = L.symbol sc

-- | Parse something surrounded by parens.
parens :: Parser a -> Parser a
parens = between (symbol "(") (symbol ")")

-- | Parse something surrounded by braces.
braces :: Parser a -> Parser a
braces = between (symbol "{") (symbol "}")

-- | Parse something surrounded by brackets.
brackets :: Parser a -> Parser a
brackets = between (symbol "[") (symbol "]")

-- | Parse a semicolon.
semi :: Parser String
semi = symbol ";"

-- | Parse a comma.
comma :: Parser String
comma = symbol ","

-- | Parse an integer.
integer :: Parser Integer
integer = L.signed space $ lexeme num
  where
    num =  string "0x" *> L.hexadecimal
       <|> string "0o" *> L.octal
       <|> string "0b" *> L.binary
       <|> L.decimal

-- | Parse a floating point number.
float :: Parser Double
float = L.signed space $ lexeme L.float

-- | List of reserved keywords.
reservedKeywords :: [String]
reservedKeywords =
    [ "if", "then", "else", "let", "in", "and", "or", "not"
    , "False", "True", "Any", "Str", "Int", "Float", "Bool", "Slice"
    ]

-- | List of reserved structure names.
reservedStructs :: [String]
reservedStructs = ["Index", "Series", "DataFrame", "Loc", "ILoc", "GroupBy"]

-- | List of reserved names.
reservedNames :: [String]
reservedNames = reservedKeywords ++ reservedStructs

-- | Parse the first character of an identifier.
startIdentChar :: Parser Char
startIdentChar = letterChar <|> char '_'

-- | Parse a character of an identifier.
identChar :: Parser Char
identChar = alphaNumChar <|> char '_'

-- | Parse a character of an operator.
opChar :: Parser Char
opChar = oneOf ":!#$%&*+./<=>?@\\^|-~" <?> "operator character"

-- | Parse a single identifier that doesn't conflict with a keyword.
identifier :: Parser String
identifier = (lexeme . try) (ident >>= check) <?> "identifier"
  where
    ident   = (:) <$> startIdentChar <*> many identChar
    check i = if i `elem` reservedNames then fail $ "unexpected keyword " ++ i else pure i

-- | Parse a reserved keyword.
reserved :: String -> Parser ()
reserved name = (lexeme . try) (string name *> notFollowedBy identChar) <?> "keyword " ++ name

-- | Parse a reserved operator.
reservedOp :: String -> Parser ()
reservedOp name = (lexeme . try) (string name *> notFollowedBy opChar) <?> "operator " ++ name

-- TODO: add escapes, potentially allow single quote strings?
-- | Parse a string literal.
stringLiteral :: Parser String
stringLiteral = lexeme $ between (char '"') (char '"') (many stringChar)
  where
    stringChar = satisfy (\c -> c /= '"' && c > '\026')  -- Taken from Text.Parsec.Token
