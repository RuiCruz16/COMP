package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
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

        addVisit("ParameterList", this::visitParams);
        addVisit("ExprStmt", this::visitExpr);
        addVisit(OBJECT_METHOD, this::visitObjectMethod);
        addVisit("If", this::visitIfStmt);
        //addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit("ScopeStmt", this::visitScope);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitIfStmt(JmmNode jmmNode, Void unused) {
        System.out.println("visitIfStmt " + jmmNode.getChildren());
        StringBuilder code = new StringBuilder();
        System.out.println("IF ELSE: " + jmmNode.getChild(0).getChildren());
        code.append("if");
        code.append(SPACE);
        code.append("(");
        OllirExprResult exprResult = exprVisitor.visit(jmmNode.getChild(0).getChild(0), unused);
        code.append(exprResult.getCode());
        code.append(")");
        code.append(" goto then;");
        code.append(NL);
        if(jmmNode.getChild(0).getChildren("StmtScope").size() == 2) {
            code.append("else:");
        }
        else code.append("then:");
        code.append(NL);

        int i = 0;
        for(JmmNode child : jmmNode.getChild(0).getChildren("StmtScope")) {
            if(i == 1) {
                code.append("then:");
                code.append(NL);
            }
            for(JmmNode scopeChild : child.getChildren().reversed()) {
                code.append(visit(scopeChild, unused));
            }
            code.append(NL);
            code.append("goto end;");
            code.append(NL);
            i++;
        }
        if(!jmmNode.getAncestor(METHOD_DECL).get().getChildren().getLast().equals(jmmNode)) {
            code.append("end: ");
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
        System.out.println("visitAssignStmt " + node.getChildren());
        System.out.println("ASSIGN STMT " + node.toString());
        System.out.println("ASSIGN STMT CHILDREN" + node.getChildren());
        System.out.println("KIND: "+ node.getChild(1).getKind());
        var rhs = exprVisitor.visit(node.getChild(1));
        System.out.println("RHS");
        System.out.println(rhs.getCode());
        System.out.println(rhs.getComputation());
        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        var left = node.getChild(0);
        Type thisType = types.getExprType(left);
        String typeString = ollirTypes.toOllirType(thisType);
        var varCode = left.get("name") + typeString;


        code.append(varCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(SPACE);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitExpr(JmmNode jmmNode, Void unused) {
        System.out.println("JMM node " + jmmNode.getChildren());
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
            else {
                code.append(child.get("value"));
            }
            Type paramType = types.getExprType(child);
            String typeCode = ollirTypes.toOllirType(paramType);

            code.append(typeCode);
        }
        code.append(").V;");
        return code.toString();
    }

    private String visitNewArray(JmmNode jmmNode, Void unused) {
        System.out.println("VISIT NEW ARRAY");
        System.out.println("JMM node " + jmmNode.getChildren());
        System.out.println(jmmNode);
        return "newarray(";
    }

    private String visitReturn(JmmNode node, Void unused) {
        // TODO: Hardcoded for int type, needs to be expanded
        System.out.println("VISIT RETURN");
        System.out.println("Jmm node " + node.getChildren());
        System.out.println(node);
        Type type = types.getExprType(node.getChild(0));
        System.out.println("type: " + type);


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
        StringBuilder code = new StringBuilder();
        for (JmmNode child : node.getChildren()) {
            code.append(visitParam(child, unused));
            if(node.getChildren().getLast().equals(child)) continue;
            code.append(COMMA);
        }
        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        JmmNode paramNode = node.getChild(0);
        var typeCode = ollirTypes.toOllirType(paramNode.getChild(0));
        var id = paramNode.get("name");

        return id + typeCode;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");
        System.out.println("NODE" + node.getChildren());
        System.out.println("NODE ASFSD:  " + node);
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
        // TODO: Hardcoded for a single parameter, needs to be expanded
        var paramsCode = visit(node.getChild(1));

        code.append("(" + paramsCode + ")");

        // type
        // TODO: Hardcoded for int, needs to be expanded
        Type type = TypeUtils.convertType(node.getChild(0));

        var retType = ollirTypes.toOllirType(type);
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);
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

        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

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
        System.out.println("VISIT PROGRAM");
        System.out.println("Jmm node " + node.getChildren());
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
        System.out.println("DEFAULT:  " + node);
        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
