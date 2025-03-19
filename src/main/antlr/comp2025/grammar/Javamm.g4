grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : 'int' ;
BOOL : 'bool';
STR : 'string';
PUBLIC : 'public' ;
RETURN : 'return' ;
THIS : 'this' ;
ARRAY: 'array';
VOID: 'void';
IMPORT: 'import';
EXTENDS: 'extends';
STATIC: 'static';
MAIN: 'main';
WHILE: 'while';
IF: 'if';
ELSE: 'else';
NEW: 'new';

MULTILINECOMMENT : '/*'(.)*?'*/' -> skip;
SINGLELINECOMMENT : '//'(.)*?'\n' -> skip;
INTEGER : '0' | [1-9][0-9]* ;
STRING: '"' ( ~["\\] | '\\' . )* '"';
BOOLEAN: 'true' | 'false';

ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;
WS : [ \t\n\r\f]+ -> skip;

program
    : importDecl* classDecl EOF
    ;

classDecl
    : CLASS name=ID (extendsClause)?
        '{'
        varDecl*
        methodDecl*
        '}'
    ;

extendsClause
    : EXTENDS qualifiedName
    ;

qualifiedName
    : superclass=ID
    ;

varDecl
    : varType=type name=ID ';'
    ;

typeID
    : name=(INT | STR | BOOL | ID | VOID); // we include ID to take into account objects

type
    : name=(INT | STR | BOOL | ID | VOID) suffix=( '[]' | '...' )?
    ;


importDecl :IMPORT pck+=ID ('.'pck+=ID)* ';';

methodDecl locals[boolean isPublic=false]
    : (PUBLIC { $isPublic = true; })?
      (
         returnType=type name=ID
       | STATIC returnType=type name=MAIN
      )
      parameters=parameterList
      '{' varDecl* stmt* '}'
    ;

parameterList
    : '(' params? ')'
    ;

params
    : par+=param (',' par+=param)*
    ;

param
    : paramType=type name=ID
    ;


scopeStmt
    : '{' varDecl* stmt* '}';

whileStmt
    : WHILE '(' condition=expr ')' (stmt);

ifStmt
    : IF '(' condition=expr ')' (stmt) (ELSE (stmt))?;

stmt
    : whileStmt #While
    | ifStmt #If
    | expr '=' expr ';' #AssignStmt //
    | RETURN expr ';' #ReturnStmt
    | scopeStmt #StmtScope
    | expr ';' #ExprStmt
    ;

typeValue
    : '[' typeValue (',' typeValue)* ']' #ARRAY
    | INTEGER # IntLit
    | STRING #StringLit
    | BOOLEAN #BooleanLit
    | name=ID # Var
    ;

methodCall
    : ('.' name=ID '(' (typeValue (',' typeValue)*)? ')')+;

newObject
    : NEW name=ID '(' ((typeValue | ID) (',' (typeValue | ID))*)? ')';

newArray
    : NEW typeID'['expr']';

arrayLit
    : '[' ( expr ( ',' expr )* )? ']'
    ;

expr
    : '(' expr ')' #ParenthesesExpr
    | op='!' expr #NegExpr //
    | expr op= ('*'|'/'|'%') expr #BinaryExpr //
    | expr op= ('+'|'-') expr #BinaryExpr //
    | expr op= ('<'|'>'|'<='|'>='|'instanceof') expr #BinaryExpr //
    | expr op= '&' expr #BinaryExpr //
    | expr op= '^' expr #BinaryExpr //
    | expr op= '|' expr #BinaryExpr //
    | expr op= '&&' expr #BinaryExpr //
    | expr op= '||' expr #BinaryExpr //
    | expr'[' index=expr ']' #ArrayAccess
    | value=INTEGER #IntegerLiteral //
    | value=BOOLEAN #BooleanLiteral
    | value=STRING #StringLiteral
    | var=(THIS | ID) '.'suffix=ID ('.' expr)? #ObjectAttribute
    | var=(THIS | ID) '.'suffix=ID ('('((typeValue | ID) (',' (typeValue | ID))*)?')') ('.' expr)? #ObjectMethod
    | expr methodCall #CallMethod
    | value=THIS #This
    | newObject #ObjectNew
    | newArray #ArrayNew
    | name=ID #VarRefExpr
    | arrayLit #ArrayInit
    ;
