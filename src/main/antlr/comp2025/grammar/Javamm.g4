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

MULTILINECOMMENT : '/*'(.)*?'*/' -> skip;
SINGLELINECOMMENT : '//'(.)*?'\n' -> skip;
INTEGER : [0-9]+ ;
STRING: '"' ( ~["\\] | '\\' . )* '"';
BOOLEAN: 'true' | 'false';

ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;
WS : [ \t\n\r\f]+ -> skip;

program
    : importDecl* classDecl EOF
    ;

classDecl
    : CLASS name=ID (extendsOrImplementsClause)?
        '{'
        varDecl*
        methodDecl*
        '}'
    ;

extendsOrImplementsClause
    : 'extends' superclass=qualifiedName ('implements' interfaceList)?
    | 'implements' interfaceList
    ;

qualifiedName
    : ID ('.' ID)*
    ;

interfaceList
    : qualifiedName (',' qualifiedName)*
    ;

varDecl
    : type name=ID ';'
    ;

typeID
    : (INT | STR | BOOL | ID); // we include ID to take into account objects

type
    : name=typeID #SimpleObject
    | name=typeID'[]' #Array
    | name=typeID'...' #VarArgs
    ;

importDecl :'import ' name=ID('.'ID)* ';';

mainDecl
    : 'static void main'
    '(' params ')'
    '{' varDecl* stmt* '}'
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        '(' params ')'
        '{' varDecl* stmt* '}'
    | mainDecl
    ;

param
    : type name=ID
    ;

params
    : param (',' param)*
    ;

scopeStmt
    : '{' varDecl* stmt* '}';

whileStmt
    : 'while' '(' (ID|BOOLEAN) ')' (stmt | scopeStmt);

ifStmt
    : 'if' '(' (ID|BOOLEAN) ')' (stmt | scopeStmt) ('else ' (stmt | scopeStmt))?;

stmt
    : whileStmt #While
    | ifStmt #If
    | expr '=' expr ';' #AssignStmt //
    | RETURN expr ';' #ReturnStmt
    | scopeStmt #StmtScope
    ;

typeValue
    : (INTEGER | STRING | BOOLEAN);

methodCall
    : ('.'ID'(' ((typeValue | ID) (',' (typeValue | ID))*)? ')')+;

newObject
    : 'new' ID '(' ((typeValue | ID) (',' (typeValue | ID))*)? ')';

newArray
    : 'new' typeID'['expr']';

// TODO, add operators taking into account precedence
expr
    : '(' expr ')' #ParenthesesExpr
    | op='!' expr #BinaryExpr //
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
    | value=STRING #BooleanLiteral
    | value=THIS #This
    | value='[' typeValue (','typeValue)*']' #ArrayLiteral
    | expr value='.' ID+ #ObjectAttribute
    | expr methodCall #CallMethod
    | newObject #ObjectNew
    | newArray #ArrayNew
    | name=ID #VarRefExpr //
    ;
