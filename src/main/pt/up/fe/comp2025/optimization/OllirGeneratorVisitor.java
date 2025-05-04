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
        addVisit(CALL_METHOD, this::visitCallMethod);
        addVisit("If", this::visitIfStmt);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit("StmtScope", this::visitStmtScope);
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

        var whileResult = visit(jmmNode.getChild(0).getChildren().getLast(), unused);
        code.append(whileResult);

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

        JmmNode elseNode = jmmNode.getChild(0).getChildren().getLast();
        var elseResult = visit(elseNode, unused);
        code.append(elseResult);

        JmmNode ifNode = jmmNode.getChild(0).getChildren().get(1);
        var ifResult = visit(ifNode, unused);

        code.append("goto ").append(auxEndif).append(END_STMT);
        code.append(auxThen).append(":").append(NL);
        code.append(ifResult);

        code.append(auxEndif).append(":").append(NL);


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
        StringBuilder code = new StringBuilder();

        var left = node.getChild(0);
        var right = node.getChild(1);

        Type thisType = types.getExprType(left);
        String typeString = ollirTypes.toOllirType(thisType);

        String leftName = left.getKind().equals("ArrayAccess")
                ? left.getChild(0).get("name")
                : left.get("name");

        String methodName;
        var methodNode = node.getAncestor("MethodDecl");
        if (methodNode.isPresent()) {
            methodName = methodNode.get().get("name");
        } else {
            methodName = null;
        }
        boolean isLeftField;
        String tempLeftName = leftName;

        if (methodName != null) {

            isLeftField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(tempLeftName)) && table.getParameters(methodName).stream().noneMatch(o -> o.getName().equals(tempLeftName)) && table.getLocalVariables(methodName).stream().noneMatch(o -> o.getName().equals(tempLeftName));
        }
        else {
            isLeftField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(tempLeftName));
        }


        StringBuilder lhsComputation = new StringBuilder();
        String varCode = "";

        if (leftName.equals("String")) {
            leftName = "\"String\"";
        }

        if (left.getKind().equals("ArrayAccess")) {
            var indexNode = left.getChild(1);

            if (isLeftField) {
                var lhs = exprVisitor.visit(left);
                lhsComputation.append(lhs.getComputation());
                varCode = lhs.getCode();
            } else {
                var leftIndex = exprVisitor.visit(indexNode);
                lhsComputation.append(leftIndex.getComputation());

                varCode = leftName + "[" + leftIndex.getCode() + "]" + typeString;
            }
        } else {
            varCode = leftName + typeString;
        }

        var rhs = exprVisitor.visit(right);
        String rhsCode = rhs.getCode();

        code.append(lhsComputation);
        code.append(rhs.getComputation());

        if (isLeftField && !left.getKind().equals("ArrayAccess")) {
            code.append("putfield(this").append(COMMA)
                    .append(leftName).append(typeString).append(COMMA)
                    .append(rhsCode).append(").V;\n");
        } else {
            code.append(varCode).append(SPACE)
                    .append(ASSIGN).append(typeString).append(SPACE)
                    .append(rhsCode).append(END_STMT);;
        }

        return code.toString();
    }

    private String visitExpr(JmmNode jmmNode, Void unused) {
        String code = "";
        for (JmmNode child : jmmNode.getChildren()) {
            code = visit(child, unused);
        }
        return code;
    }

    private String visitCallMethod(JmmNode jmmNode, Void unused) {
        var exprResult = exprVisitor.visit(jmmNode);

        StringBuilder code = new StringBuilder();
        code.append(exprResult.getComputation());

        return code.toString();
    }

    private String visitObjectMethod(JmmNode jmmNode, Void unused) {
        String methodName = jmmNode.get("suffix");
        String varName = jmmNode.get("var");
        StringBuilder code = new StringBuilder();
        OllirExprResult exprResult = null;

        if(!jmmNode.getChildren().isEmpty()) {
            exprResult = exprVisitor.visit(jmmNode.getChild(0));
            code.append(exprResult.getComputation());
        }

        if(varName.equals("this")) {
            varName += ".";
            varName += table.getClassName();
            code.append("invokevirtual(").append(varName);
        }
        else {
            code.append("invokestatic(").append(varName);
        }

        code.append(COMMA).append("\"").append(methodName).append("\"");

        if(exprResult != null) {
            code.append(COMMA);
            code.append(exprResult.getCode());
        }

        String ollirReturnType = table.getMethods().stream()
                .filter(m -> m.equals(methodName))
                .findFirst()
                .map(m -> ollirTypes.toOllirType(table.getReturnType(m)))
                .orElse(".V");

        code.append(")").append(ollirReturnType).append(END_STMT);
        code.append(NL);

        return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;

        code.append(expr.getComputation());
        code.append("ret");
        code.append(ollirTypes.toOllirType(new Type(node.getParent().getChild(0).get("name"), false)));
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

    private boolean hasVarArgs(JmmNode node) {
        if(node.getChildren().isEmpty()) return false;
        JmmNode paramNode = node.getChildren().getLast();
        JmmNode paramType = paramNode.getChildren().getLast();

        try {
            return !paramType.getChildren().getLast().getChildren().getFirst().getChildren("VarArgsSuffix").isEmpty();
        } catch (Exception ignored) { }

        return false;
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");
        boolean isPublic = node.getBoolean("isPublic", false);

        if (isPublic) {
            code.append("public ");
        }

        if(hasVarArgs(node.getChild(1))) {
            code.append("varargs ");
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

    private String visitStmtScope(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        code.append(visit(node.getChild(0)));
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
