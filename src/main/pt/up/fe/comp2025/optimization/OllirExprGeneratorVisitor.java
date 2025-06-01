package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String COMMA = ", ";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;
    private final List<String> operators = List.of("+", "-", "*", "/");


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
    }

    public void setTypesCurrentMethod(String name) {
        this.types.setCurrentMethod(name);
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit("IntLit", this::visitInteger);
        addVisit("BooleanLiteral", this::visitBool);
        addVisit("ArrayNew", this::visitNewArray);
        addVisit("TypeID", this::visitTypeId);
        addVisit("If", this::visitIfStmt);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(ARRAY_INIT, this::visitArrayInit);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit("ObjectNew", this::visitObjectNew);
        addVisit(OBJECT_METHOD, this::visitObjectMethod);
        addVisit("ObjectAttribute", this::visitObjectAttribute);
        addVisit("MethodCall", this::visitMethodCall);
        addVisit("CallMethod", this::visitCallMethod);
        addVisit("ParenthesesExpr", this::visitParenthesesExpr);
        addVisit("NegExpr", this::visitNegExpr);
        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitBool(JmmNode node, Void unused) {
        Type type = types.getExprType(node);
        String ollirBoolType = ollirTypes.toOllirType(type);
        String code = (node.get("value").equals("true") || node.get("value").equals("1") ? "1" : "0") + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitTypeId(JmmNode node, Void unused) {
        String type = ollirTypes.toOllirType(new Type(node.get("name"), false));
        return new OllirExprResult(type);
    }

    private OllirExprResult visitIfStmt(JmmNode node, Void unused) {
        return new OllirExprResult("if");
    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        JmmNode arrayNew = node.getChildren().getFirst();
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        Type typeArray = null;
        OllirExprResult expr = null;

        for (JmmNode child : arrayNew.getChildren()) {
            if (child.getKind().equals("NewArray")) continue;

            if (child.getKind().equals("TypeID")) {
                typeArray = new Type(child.get("name"), true);
            } else if (types.getExprType(child) != null) {
                expr = visit(child, null);
                computation.append(expr.getComputation());
            }
        }

        String temp = ollirTypes.nextTemp();
        String ollirType = ollirTypes.toOllirType(Objects.requireNonNull(typeArray));
        code.append(temp).append(ollirType);

        computation.append(temp).append(ollirType)
                .append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(array, ").append(Objects.requireNonNull(expr).getCode()).append(")")
                .append(ollirType).append(END_STMT);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        // code to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code;
        if (node.get("op").equals("&&")) {

            var lhs = visit(node.getChild(0));
            var rhs = visit(node.getChild(1));

            computation.append(lhs.getComputation());
            String auxTrue = ollirTypes.nextTemp("true_path");
            String auxAndTmp = ollirTypes.nextTemp("andTmp");
            computation.append("if (").append(lhs.getCode()).append(")").append(" goto ").append(auxTrue).append(END_STMT);
            computation.append(auxAndTmp).append(resOllirType).append(SPACE).append(ASSIGN).append(resOllirType);
            computation.append(SPACE).append("0").append(resOllirType).append(END_STMT);
            String auxEnd = ollirTypes.nextTemp("end");
            computation.append("goto ").append(auxEnd).append(END_STMT);
            computation.append(auxTrue).append(":").append(NL);
            computation.append(rhs.getComputation());
            computation.append(auxAndTmp).append(resOllirType).append(SPACE).append(ASSIGN).append(resOllirType);
            computation.append(SPACE).append(rhs.getCode()).append(END_STMT);
            computation.append(auxEnd).append(":").append(NL);
            code = auxAndTmp + resOllirType;
            return new OllirExprResult(code, computation);
        }

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        code = ollirTypes.nextTemp() + resOllirType;


        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = types.getExprType(node);
        computation.append(node.get("op")).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitParenthesesExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitNegExpr(JmmNode node, Void unused) {
        var expr = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(expr.getComputation());

        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append("!").append(ollirTypes.toOllirType(resType)).append(SPACE)
                .append(expr.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("name");
        String methodName;
        var methodNode = node.getAncestor("MethodDecl");
        if (methodNode.isPresent()) {
            methodName = methodNode.get().get("name");
        } else {
            methodName = null;
        }
        Type type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        boolean isField;

        if (methodName != null) {
            isField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(id)) && table.getParameters(methodName).stream().noneMatch(o -> o.getName().equals(id)) && table.getLocalVariables(methodName).stream().noneMatch(o -> o.getName().equals(id));
        }
        else {
            isField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(id));
        }

        String code;
        StringBuilder computation = new StringBuilder();


        if (isField) {
            code = ollirTypes.nextTemp() + ollirType;
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(ollirType).append(SPACE)
                    .append("getfield(this").append(COMMA).append(id).append(ollirType).append(")").append(ollirType).append(END_STMT);
        } else {
            code = id + ollirType;
            computation = new StringBuilder("");
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        if (node.getChild(0).getKind().equals("ArrayInit")) {
            var arrayInit = visit(node.getChild(0));
            var index = visit(node.getChild(1));
            StringBuilder code = new StringBuilder();
            StringBuilder computation = new StringBuilder();

            computation.append(arrayInit.getComputation());
            computation.append(index.getComputation());
            String typeLiteral = ollirTypes.toOllirType(types.getExprType(node.getChild(0).getChild(0).getChild(0)));

            code.append(ollirTypes.nextTemp()).append(typeLiteral);
            computation.append(code).append(SPACE).append(ASSIGN).append(typeLiteral).append(SPACE).append(arrayInit.getCode())
                       .append("[").append(index.getCode()).append("]").append(typeLiteral).append(END_STMT);

            return new OllirExprResult(code.toString(), computation);
        }

        Type nodeType = types.getExprType(node);
        String name = node.getChild(0).get("name");
        String typeString = ollirTypes.toOllirType(nodeType);
        Type arrayType = types.getVarType(node.getChildren(Kind.VAR_REF_EXPR.toString()).getFirst().get("name"));

        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        String methodName;
        var methodNode = node.getAncestor("MethodDecl");
        if (methodNode.isPresent()) {
            methodName = methodNode.get().get("name");
        } else {
            methodName = null;
        }
        boolean isField;

        if (methodName != null) {
            isField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(name)) && table.getParameters(methodName).stream().noneMatch(o -> o.getName().equals(name)) && table.getLocalVariables(methodName).stream().noneMatch(o -> o.getName().equals(name));
        }
        else {
            isField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(name));
        }

        var indexNode = node.getChild(1);
        if (isField) {
            Type fieldType = types.getVarType(name);
            String fieldTypeString = ollirTypes.toOllirType(fieldType);
            String fieldTemp = ollirTypes.nextTemp();
            StringBuilder lhsComputation = new StringBuilder();
            lhsComputation.append(fieldTemp).append(fieldTypeString).append(SPACE)
                    .append(ASSIGN).append(fieldTypeString).append(SPACE)
                    .append("getfield(this").append(COMMA).append(name)
                    .append(fieldTypeString).append(")").append(fieldTypeString)
                    .append(END_STMT);

            var leftIndex = visit(indexNode);
            lhsComputation.append(leftIndex.getComputation());
            computation.append(lhsComputation);

            if ((node.getParent().getKind().equals("AssignStmt") && node.getParent().getChild(1).getKind().equals("ArrayAccess")) || node.getParent().getKind().equals("ReturnStmt") || node.getParent().getKind().equals("ObjectMethod") || node.getParent().getKind().equals("ObjectNew")) {
                code.append(ollirTypes.nextTemp()).append(typeString);

                computation.append(code).append(SPACE).append(ASSIGN).append(typeString).append(SPACE).append(fieldTemp)
                        .append(fieldTypeString).append("[").append(leftIndex.getCode())
                        .append("]").append(typeString).append(END_STMT);
            } else {
                code.append(fieldTemp).append(fieldTypeString).append("[").append(leftIndex.getCode()).append("]").append(typeString);
            }
            return new OllirExprResult(code.toString(), computation);
        }

        var expr = visit(node.getChild(1), unused);

        code.append(ollirTypes.nextTemp()).append(typeString);

        computation.append(expr.getComputation());
        computation.append(code);
        computation.append(SPACE).append(ASSIGN).append(typeString);
        computation.append(SPACE).append(name).append(ollirTypes.toOllirType(arrayType));
        computation.append("[").append(expr.getCode()).append("]");
        computation.append(typeString).append(END_STMT);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitArrayInit(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        String typeNode = ollirTypes.toOllirType(types.getExprType(node));
        String tempName = ollirTypes.nextTemp();

        String code = tempName + typeNode;

        computation.append(code).append(SPACE).append(ASSIGN).append(typeNode).append(SPACE)
                   .append("new(array, ").append(node.getChild(0).getNumChildren())
                   .append(ollirTypes.toOllirType(TypeUtils.newIntType())).append(")").append(typeNode).append(END_STMT);

        for (int i = 0; i < node.getChild(0).getNumChildren(); i++) {
            if (types.getExprType(node.getChild(0).getChild(i)) != null) {
                String typeLiteral = ollirTypes.toOllirType(types.getExprType(node.getChild(0).getChild(i)));
                var expr = visit(node.getChild(0).getChild(i));
                computation.append(expr.getComputation());
                computation.append(tempName).append("[").append(i).append(typeLiteral).append("]").append(typeLiteral).append(SPACE)
                        .append(ASSIGN).append(typeLiteral).append(SPACE).append(expr.getCode()).append(END_STMT);
            }
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitObjectNew(JmmNode node, Void unused) {
        String className = node.getChild(0).get("name");

        boolean isJvmClass = className.equals("String");
        String quotedClassName = "\"" + className + "\"";
        String displayName = isJvmClass ? quotedClassName : className;

        String temp = ollirTypes.nextTemp();
        StringBuilder code = new StringBuilder();
        code.append(temp).append(".").append(displayName);

        StringBuilder computation = new StringBuilder();
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(".").append(displayName).append(SPACE)
                .append("new(").append(displayName).append(")")
                .append(".").append(displayName).append(END_STMT);

        computation.append("invokespecial(")
                .append(code).append(", \"<init>\").V").append(END_STMT);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitObjectAttribute(JmmNode node, Void unused) {
        String objectName = node.get("var");
        Type objectType = types.getVarType(objectName);
        String objectOllirTypeSimple = ollirTypes.toOllirType(new Type(objectType.getName(), false));
        String objectOllirType = ollirTypes.toOllirType(objectType);

        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        String methodName;
        var methodNode = node.getAncestor("MethodDecl");
        if (methodNode.isPresent()) {
            methodName = methodNode.get().get("name");
        } else {
            methodName = null;
        }
        boolean isField;

        if (methodName != null) {
            isField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(objectName)) && table.getParameters(methodName).stream().noneMatch(o -> o.getName().equals(objectName)) && table.getLocalVariables(methodName).stream().noneMatch(o -> o.getName().equals(objectName));
        }
        else {
            isField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(objectName));
        }

        String arrayRef;

        if (isField) {
            String fieldTemp = ollirTypes.nextTemp();

            computation.append(fieldTemp).append(objectOllirType).append(SPACE)
                    .append(ASSIGN).append(objectOllirType).append(SPACE)
                    .append("getfield(this").append(COMMA)
                    .append(objectName).append(objectOllirType).append(")")
                    .append(objectOllirType).append(END_STMT);

            arrayRef = fieldTemp + objectOllirType;
        } else {
            arrayRef = objectName + objectOllirType;
        }

        code.append(ollirTypes.nextTemp()).append(objectOllirTypeSimple);

        computation.append(code).append(SPACE).append(ASSIGN).append(objectOllirTypeSimple).append(SPACE)
                .append("arraylength(").append(arrayRef).append(")").append(objectOllirTypeSimple)
                .append(END_STMT);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitMethodCall(JmmNode jmmNode, Void unused) {
        var objectMethod = visit(jmmNode.getParent().getChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(objectMethod.getComputation());

        String methodName = jmmNode.get("name");
        Type type = types.getExprType(jmmNode.getParent());

        // Type is null when using an imported class
        if (type == null && jmmNode.getParent().getParent().getKind().equals(Kind.ASSIGN_STMT.toString())) {
            type = types.getExprType(jmmNode.getParent().getParent().getChild(0));
        }

        if (type == null && jmmNode.getParent().getParent().getKind().equals(Kind.RETURN_STMT.toString())) {
            type = table.getReturnType(jmmNode.getParent().getParent().getParent().get("name"));
        }

        StringBuilder invokeCode = new StringBuilder();
        invokeCode.append("invokevirtual(").append(objectMethod.getCode()).append(COMMA).append("\"").append(methodName).append("\"");

        for (int i = 0; i < jmmNode.getChildren().size(); i++) {
            var expr = visit(jmmNode.getChild(i));

            computation.append(expr.getComputation());
            invokeCode.append(COMMA).append(expr.getCode());
        }
        invokeCode.append(")").append(ollirTypes.toOllirType(type)).append(END_STMT);

        String code = ollirTypes.nextTemp() + ollirTypes.toOllirType(type);
        computation.append(code).append(SPACE).append(ASSIGN).append(ollirTypes.toOllirType(type)).append(SPACE);
        computation.append(invokeCode);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitCallMethod(JmmNode jmmNode, Void unused) {
        if (jmmNode.getParent().getKind().equals("ExprStmt")) {
            String type = "." + jmmNode.getChild(0).getChild(0).get("name");
            String tempName = ollirTypes.nextTemp() + type;
            StringBuilder computation = new StringBuilder();

            computation.append(tempName).append(SPACE).append(ASSIGN).append(type).append(SPACE).append("new(").append(jmmNode.getChild(0).getChild(0).get("name")).append(")").append(type).append(END_STMT);
            computation.append("invokespecial(").append(tempName).append(", \"<init>\").V").append(END_STMT);

            StringBuilder invokeComputation = new StringBuilder();
            invokeComputation.append("invokevirtual(").append(tempName).append(COMMA).append("\"").append(jmmNode.getChild(1).get("name")).append("\"");

            for (JmmNode child : jmmNode.getChild(1).getChildren()) {
                var exprResult = visit(child);
                invokeComputation.append(COMMA);
                invokeComputation.append(exprResult.getCode());
                computation.append(exprResult.getComputation());
            }

            boolean isClass = table.getClassName().equals(jmmNode.getChild(0).getChild(0).get("name"));
            String aux = isClass ? ollirTypes.toOllirType(table.getReturnType(jmmNode.getChild(1).get("name"))) : ".V";
            invokeComputation.append(")").append(aux).append(END_STMT);
            computation.append(invokeComputation);

            return new OllirExprResult("", computation);
        }

        var method = visit(jmmNode.getChild(1));

        String code = method.getCode();
        StringBuilder computation = new StringBuilder();
        computation.append(method.getComputation());

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitObjectMethod(JmmNode node, Void unused) {
        String methodName = node.get("suffix");
        String varName = node.get("var");
        String ollirType;
        if (types.getExprType(node) == null) {
            if (node.getParent().getChild(0).getKind().equals("ArrayAccess")) {
                ollirType = ollirTypes.toOllirType(new Type(types.getVarType(node.getParent().getChild(0).getChild(0).get("name")).getName(), false));
            } else if (node.getParent().getKind().equals("ArrayAccess")) {
                if (node.getParent().getChild(0).getKind().equals("ArrayInit")) {
                    ollirType = ollirTypes.toOllirType(new Type("int", false));
                } else {
                    ollirType = ollirTypes.toOllirType(new Type(types.getVarType(node.getParent().getChild(0).get("name")).getName(), false));
                }
            } else if (node.getParent().getKind().equals("ReturnStmt")) {
                ollirType = ollirTypes.toOllirType(new Type(node.getParent().getParent().getChild(0).get("name"), false));
            }
            else {
                ollirType = ollirTypes.toOllirType(types.getVarType(node.getParent().getChild(0).get("name")));
            }
        } else {
            if (node.getParent().getKind().equals("AssignStmt")) {
                if (node.getParent().getChild(0).getKind().equals("ArrayAccess")) {
                    ollirType = ollirTypes.toOllirType(new Type(types.getVarType(node.getParent().getChild(0).getChild(0).get("name")).getName(), false));
                } else {
                    ollirType = ollirTypes.toOllirType(types.getVarType(node.getParent().getChild(0).get("name")));
                }
            } else {
                ollirType = ollirTypes.toOllirType(types.getExprType(node));
            }
        }

        String principalMethodName;
        var principalMethodNode = node.getAncestor("MethodDecl");
        if (principalMethodNode.isPresent()) {
            principalMethodName = principalMethodNode.get().get("name");
        } else {
            principalMethodName = null;
        }
        boolean isField;

        if (principalMethodName != null) {
            isField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(varName)) && table.getParameters(principalMethodName).stream().noneMatch(o -> o.getName().equals(varName)) && table.getLocalVariables(principalMethodName).stream().noneMatch(o -> o.getName().equals(varName));
        }
        else {
            isField = table.getFields().stream()
                    .anyMatch(f -> f.getName().equals(varName));
        }

        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        String caller = "";

        if (isField) {
            String fieldTypeString = ollirTypes.toOllirType(types.getVarType(varName));
            String fieldTemp = ollirTypes.nextTemp();
            computation.append(fieldTemp).append(fieldTypeString).append(SPACE).append(ASSIGN).append(fieldTypeString)
                    .append(SPACE).append("getfield(this").append(COMMA).append(varName)
                    .append(fieldTypeString).append(")").append(fieldTypeString).append(END_STMT);
            if(varName.equals("this")){
                caller += "invokevirtual(this." + table.getClassName();
            }
            else {
                caller += "invokevirtual(";
                caller += fieldTemp + fieldTypeString;
            }
        } else {
            Type varType = types.getVarType(varName);
            if(varType == null && types.isTypeInImports(varName, table.getImports())) {

                caller = "invokestatic(" + varName;
            }
            else caller = "invokevirtual(" + varName + ollirTypes.toOllirType(types.getVarType(varName));
        }

        StringBuilder invoke = new StringBuilder();
        invoke.append(caller).append(COMMA);
        invoke.append("\"").append(methodName).append("\"");

        for (int i = 0; i < node.getChildren().size(); i++) {
            var expr = visit(node.getChild(i));
            computation.append(expr.getComputation());

            invoke.append(COMMA).append(expr.getCode());
        }

        code.append(ollirTypes.nextTemp()).append(ollirType);

        invoke.append(")").append(ollirType).append(END_STMT);

        computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE);
        computation.append(invoke);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitVarDecl(JmmNode node, Void unused) {
        String code = node.get("value");
        return new OllirExprResult(code);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
