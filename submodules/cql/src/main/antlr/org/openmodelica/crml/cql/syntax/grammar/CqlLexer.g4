/*
 * The tokens of CQL (QUERY_LANGUAGE_SPEC.md, section 4.1).
 *
 * Line breaks are whitespace (frontend plan, F2): a statement ends at ";", at the end
 * of the file, or where the grammar cannot continue. Comments go to the hidden
 * channel, so that tools can read them; whitespace is skipped.
 *
 * The lexer never fails: what is not a token of the language is an error token
 * (UNTERMINATED_STRING, UNTERMINATED_NAME, UNKNOWN_CHAR), which the scanner turns
 * into a diagnostic with a span. Error tokens stay on the default channel, so that
 * the parser does not accept the statement they are in.
 */
lexer grammar CqlLexer;

@members {
    /** Whether c can continue a name: [A-Za-z0-9_]. */
    private static boolean isNameChar(int c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}

// ── Keywords ────────────────────────────────────────────────────────────────
// error, warning and info are keywords only after a companion's "!", and any word
// after "." or "~" is a name: the parser handles both, so they are not keywords here.

RESOURCE  : 'resource';
AS        : 'as';
LET       : 'let';
DEFAULT   : 'default';
ALL       : 'all';
SOME      : 'some';
NO        : 'no';
IN        : 'in';
CONTAINS  : 'contains';
IS        : 'is';
IT        : 'it';
COUNT     : 'count';
UNION     : 'union';
INTERSECT : 'intersect';
EXCEPT    : 'except';
TRUE      : 'true';
FALSE     : 'false';

// ── Operators ───────────────────────────────────────────────────────────────
// "!in" and "!contains" are one token only when no name character follows:
// "!inside" is "!" "inside".

NOT_IN       : '!in' {!isNameChar(_input.LA(1))}?;
NOT_CONTAINS : '!contains' {!isNameChar(_input.LA(1))}?;

EQ     : '==';
NE     : '!=';
LE     : '<=';
GE     : '>=';
LT     : '<';
GT     : '>';
NOT    : '!';
ASSIGN : ':=';
DCOLON : '::';
COLON  : ':';
DOTDOT : '..';
DOT    : '.';
SLASH  : '/';
COMMA  : ',';
BAR    : '|';
SEMI   : ';';
LPAREN : '(';
RPAREN : ')';
LBRACK : '[';
RBRACK : ']';
LBRACE : '{';
RBRACE : '}';
ARROW  : '->';
MINUS  : '-';
TILDE  : '~';
PLUS   : '+';
STAR   : '*';
EQUALS : '=';

// ── Literals and names ──────────────────────────────────────────────────────
// A REAL needs a digit after the dot, so "0..1" is INT DOTDOT INT.

REAL : [0-9]+ '.' [0-9]+;
INT  : [0-9]+;

// A string is on one line. Any character may be escaped here; the scanner reports
// escapes other than \" and \\.
STRING : '"' ( '\\' ~[\r\n] | ~["\\\r\n] )* '"';

// A name in backquotes is never a keyword: `in`.
QUOTED_NAME : '`' ~[`\r\n]* '`';

BUILTIN : '@' [A-Za-z_] [A-Za-z0-9_]*;
NAME    : [A-Za-z_] [A-Za-z0-9_]*;

// ── Comments and whitespace ─────────────────────────────────────────────────

COMMENT : '#' ~[\r\n]* -> channel(HIDDEN);
WS      : [ \t\r\n\f﻿]+ -> skip;

// ── Errors ──────────────────────────────────────────────────────────────────
// Longest match makes STRING and QUOTED_NAME win whenever they are closed.

UNTERMINATED_STRING : '"' ( '\\' ~[\r\n] | ~["\\\r\n] )* '\\'?;
UNTERMINATED_NAME   : '`' ~[`\r\n]*;
UNKNOWN_CHAR        : .;
