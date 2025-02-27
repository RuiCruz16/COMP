grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : 'int' ;
BOOL : 'bool ';
STR : 'string ';
PUBLIC : 'public' ;
RETURN : 'return' ;
THIS : 'this' ;


MULTILINECOMMENT : '/*'(.)*?'*/' -> skip;
SINGLELINECOMMENT : '//'(.)*?'\n' -> skip;
INTEGER : [0-9]+ ;
STRING: '"' ( ~["\\] | '\\' . )* '"';

BOOLEAN: 'true' | 'false';


ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;
PACKAGE: ID ('.'ID)*;
WS : [ \t\n\r\f]+ -> skip;
TYPES: INTEGER | STRING | BOOLEAN;

ARRAY
    : '['TYPES (','(WS)*TYPES)*']';
program
    : importDecl* classDecl EOF
    ;

// TODO, support for extens
classDecl
    : CLASS name=ID
        '{'
        methodDecl*
        '}'
    ;

varDecl
    : type name=ID ';'
    ;

// TODO, add string and other types
type
    : INT name=INT
    | INT'[]' | INT'...' name=INT'[]'
    | STRING name=STR
    | BOOLEAN name=BOOL;

importDecl :'import ' name=PACKAGE ';';

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        '(' param ')'
        '{' varDecl* stmt* '}'
    ;

param
    : type name=ID
    ;

stmt
    : expr '=' expr ';' #AssignStmt //
    | RETURN expr ';' #ReturnStmt
    ;

// TODO, add operators taking into account precedence
expr
    : '(' expr ')' #ParenthesesExpr
    | expr op= ('*'|'/'|'%') expr #BinaryExpr //
    | expr op= ('+'|'-') expr #BinaryExpr //
    | expr op= ('<'|'>'|'<='|'>='|'instanceof') expr #BinaryExpr //
    | expr op= '&' expr #BinaryExpr //
    | expr op= '^' expr #BinaryExpr //
    | expr op= '|' expr #BinaryExpr //
    | expr op= '&&' expr #BinaryExpr //
    | expr op= '||' expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | value=BOOLEAN #BooleanLiteral
    | value=STRING #BooleanLiteral
    | value=THIS #This
    | value=ARRAY #ArrayLiteral
    | name=ID #VarRefExpr //
    ;
