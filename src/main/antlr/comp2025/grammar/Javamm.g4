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
    : CLASS name=ID (extendsOrImplementsClause)?
        '{'
        varDecl*
        methodDecl*
        '}'
    ;

extendsOrImplementsClause
    : 'extends' qualifiedName ('implements' interfaceList)?
    | 'implements' interfaceList
    ;

qualifiedName
    : superclass=ID
    ;

interfaceList
    : qualifiedName (',' qualifiedName)*
    ;

varDecl
    : varType=type name=ID ';'
    ;

typeID
    : name=(INT | STR | BOOL | ID | VOID); // we include ID to take into account objects

type
    : name=(INT | STR | BOOL | ID | VOID) suffix=( '[]' | '...' )?
    ;


importDecl :'import ' pck+=ID ('.'pck+=ID)* ';';

methodDecl locals[boolean isPublic=false]
    : (PUBLIC { $isPublic = true; })?
      (
         returnType=type name=ID
       | 'static' returnType=type name='main'
      )
      parameters=parameterList
      '{' varDecl* stmt* '}'
    ;

parameterList
    : '(' params? ')'
    ;

params
    : param (',' param)*
    ;

param
    : paramType=type name=ID
    ;


scopeStmt
    : '{' varDecl* stmt* '}';

whileStmt
    : 'while' '(' condition=expr ')' (stmt);

ifStmt
    : 'if' '(' condition=expr ')' (stmt) ('else' (stmt))?;

stmt
    : whileStmt #While
    | ifStmt #If
    | expr '=' expr ';' #AssignStmt //
    | RETURN expr ';' #ReturnStmt
    | scopeStmt #StmtScope
    | expr ';' #ExprStmt
    ;

typeValue
    : (INTEGER | STRING | BOOLEAN);

methodCall
    : ('.' name=ID '(' ((typeValue | ID) (',' (typeValue | ID))*)? ')')+;

newObject
    : 'new' name=ID '(' ((typeValue | ID) (',' (typeValue | ID))*)? ')';

newArray
    : 'new' typeID'['expr']';

arrayLit
    : '[' ( expr ( ',' expr )* )? ']'
    ;

// TODO, add operators taking into account precedence
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
    | value=THIS #This
    | expr value='.' ID+ #ObjectAttribute
    | expr methodCall #CallMethod
    | newObject #ObjectNew
    | newArray #ArrayNew
    | name=ID #VarRefExpr
    | arrayLit #ArrayInit
    ;
