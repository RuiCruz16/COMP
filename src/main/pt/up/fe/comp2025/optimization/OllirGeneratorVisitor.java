package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.sql.SQLOutput;
import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";
    private final String COMMA = ", ";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImportStmt);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(VAR_DECL, this::visitVarDecl);

        addVisit("ParameterList", this::visitParams);
        addVisit("ExprStmt", this::visitExpr);
        addVisit(OBJECT_METHOD, this::visitObjectMethod);
        addVisit("If", this::visitIfStmt);
        //addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit("ScopeStmt", this::visitScope);
        addVisit(WHILE, this::visitWhileStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitWhileStmt(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();

        String auxWhile = ollirTypes.nextTemp("while");

        code.append(auxWhile).append(":");
        code.append(NL);

        OllirExprResult exprResult = exprVisitor.visit(jmmNode.getChild(0).getChild(0), unused);
        code.append(exprResult.getComputation());
        code.append(NL);

        code.append("if").append(SPACE).append("(");
        code.append("!.bool ").append(exprResult.getCode());
        code.append(")");
        code.append(" goto ");
        String auxEndif = ollirTypes.nextTemp("endif");

        code.append(auxEndif).append(END_STMT);
        code.append(NL);

        for (JmmNode child : jmmNode.getChild(0).getChildren("StmtScope")) {
            for (JmmNode scopeChild : child.getChildren()) {
                code.append(visit(scopeChild, unused));
            }
        }

        code.append(NL);
        code.append("goto ").append(auxWhile).append(END_STMT);
        code.append(NL);
        code.append(auxEndif).append(":");

        return code.toString();
    }

    private String visitIfStmt(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();
        OllirExprResult exprResult = exprVisitor.visit(jmmNode.getChild(0).getChild(0), unused);
        code.append(exprResult.getComputation().toString());
        code.append("if");
        code.append(SPACE);
        code.append("(");
        code.append(exprResult.getCode());
        code.append(")");
        code.append(" goto ");
        String auxThen = ollirTypes.nextTemp("then");
        code.append(auxThen);
        code.append(END_STMT);
        code.append(NL);

        String auxEndif = ollirTypes.nextTemp("endif");

        int i = 0;
        for(JmmNode child : jmmNode.getChild(0).getChildren("StmtScope").reversed()) {
            if(i == 1) {
                code.append(auxThen).append(":");
                code.append(NL);
            }
            for(JmmNode scopeChild : child.getChildren()) {
                code.append(visit(scopeChild, unused));
            }
            if (i == 1) {
                code.append(NL);
                code.append(auxEndif).append(":");
            }
            else {
                code.append(NL);
                code.append("goto ");
                code.append(auxEndif);
                code.append(END_STMT);
                code.append(NL);
                i++;
            }
        }
        code.append(NL);
        return code.toString();
    }

    private String visitImportStmt(JmmNode jmmNode, Void unused) {

        String pck = jmmNode.get("pck")
                .replace("[", "")
                .replace("]", "")
                .replace(", ", ".");

        return "import " + pck + ";\n";
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var rhs = exprVisitor.visit(node.getChild(1));
        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(rhs.getComputation());
        // code to compute self
        // statement has type of lhs
        var left = node.getChild(0);
        Type thisType = types.getExprType(left);
        String typeString = ollirTypes.toOllirType(thisType);

        String varCode;
        if (left.getKind().equals("ArrayAccess")) {
            varCode = left.getChild(0).get("name") + "[" + left.getChild(1).get("value") + typeString + "]" + typeString;
        }
        else {
            varCode = left.get("name") + typeString;
        }

        code.append(varCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);
        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitExpr(JmmNode jmmNode, Void unused) {
        String code = "";
        for (JmmNode child : jmmNode.getChildren()) {
            code = visit(child, unused);
        }
        return code;
    }

    private String visitObjectMethod(JmmNode jmmNode, Void unused) {
        String methodName = jmmNode.get("suffix");
        String varName = jmmNode.get("var");

        StringBuilder code = new StringBuilder();
        code.append("invokestatic(").append(varName);
        code.append(COMMA).append("\"").append(methodName).append("\"").append(COMMA);

        for (JmmNode child : jmmNode.getChildren()) {
            System.out.println("CHILD: " + child);
            if(child.hasAttribute("name")) {
                code.append(child.get("name"));
            }
            else if(!child.getKind().equals("ArrayAccess")) {
                code.append(child.get("value"));
            }
            else {
                code.append(child.getChildren(VAR_REF_EXPR.toString()).getFirst().get("name"));
            }
            Type paramType = types.getExprType(child);
            String typeCode = ollirTypes.toOllirType(paramType);

            code.append(typeCode);
        }
        code.append(").V;");
        code.append(NL);
        return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {
        // TODO: Hardcoded for int type, needs to be expanded
         Type type = types.getExprType(node.getChild(0));


        StringBuilder code = new StringBuilder();


        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;


        code.append(expr.getComputation());
        code.append("ret");
        code.append(ollirTypes.toOllirType(type));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitParams(JmmNode node, Void unused) {
        if(node.getChildren().isEmpty()) {
            return "";
        }
        StringBuilder code = new StringBuilder();
        for (JmmNode child : node.getChild(0).getChildren()) {
            code.append(visitParam(child, unused));
            if(node.getChild(0).getChildren().getLast().equals(child)) break;
            code.append(COMMA);
        }
        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        return id + typeCode;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");
        boolean isPublic = node.getBoolean("isPublic", false);

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        code.append(name);
        this.types.setCurrentMethod(name);
        this.exprVisitor.setTypesCurrentMethod(name);

        // params
        var paramsCode = visit(node.getChild(1), unused);

        code.append("(" + paramsCode + ")");

        // type
        Type type = TypeUtils.convertType(node.getChild(0));

        var retType = ollirTypes.toOllirType(type);
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);
        if (retType.equals(".V")) {
            code.append("ret.V;");
        }
        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());
        if(!node.getChildren("ExtendsClause").isEmpty()) {
            JmmNode extendsClause = node.getChildren("ExtendsClause").getFirst();
            String superClassName = extendsClause.getChildren().getFirst().get("superclass");
            code.append(SPACE).append("extends ").append(superClassName);
        }
        code.append(L_BRACKET);
        code.append(NL);
        code.append(NL);


        for (var child : node.getChildren(VAR_DECL)) {
            System.out.println(child);
            var result = visit(child, unused);
            code.append(result);
        }

        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String visitVarDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        code.append(".field public " );
        code.append(node.get("name"));
        code.append(ollirTypes.toOllirType(node.getChild(0)));
        code.append(END_STMT);
        return code.toString();
    }

    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }


    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    private String visitScope(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        for(JmmNode child : node.getChildren()) {
            code.append(visit(child, unused));
        }
        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
