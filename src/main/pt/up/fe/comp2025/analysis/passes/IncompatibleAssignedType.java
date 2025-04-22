package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;
import java.util.Objects;

public class IncompatibleAssignedType extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }


    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        JmmNode varRefExpr = assignStmt.getChildren().get(0);
        JmmNode expression = assignStmt.getChildren().get(1);

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        String superClass = table.getSuper();
        String currentClass = table.getClassName();

        if (expression.getKind().equals("ObjectMethod") && expression.hasAttribute("var") && typeUtils.getVarType(expression.get("var")) != null && !typeUtils.getVarType(expression.get("var")).getName().equals(currentClass) && !typeUtils.isCommonType(typeUtils.getVarType(expression.get("var")).getName()) && superClass == null && !typeUtils.isTypeInImports(typeUtils.getVarType(expression.get("var")).getName(), table.getImports())) {
            addReport(newError(assignStmt, "Method not defined"));
        }

        Type varType = typeUtils.getExprType(varRefExpr);
        Type expressionType = typeUtils.getExprType(expression);

        // we are assigning a array index to a expr
        // such as a[0] = 1
        if(varRefExpr.getKind().equals(Kind.ARRAY_ACCESS.toString())) {
            String arrayName = varRefExpr.getChildren(Kind.VAR_REF_EXPR.toString()).getFirst().get("name");
            Type exprType = typeUtils.getExprType(varRefExpr.getChild(0));
            Type arrayType = typeUtils.getVarType(arrayName);
            if (arrayType != null && arrayType.isArray() && exprType != null && exprType.getName().equals(arrayType.getName())) {
                return null;
            }
            String message = "Array index " + varRefExpr.getChild(1).get("value") + " is not of the same type as the expression being assigned.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    varRefExpr.getChild(1).getLine(),
                    varRefExpr.getChild(1).getColumn(),
                    message,
                    null)
            );
        }

        if (expressionType != null && (expressionType.getName().equals("ArrayInit") || expressionType.getName().equals("this"))) {
            return null;
        }

        // if types are equal
        if (varType != null && varType.equals(expressionType)) {
            return null;
        }

        // if var is an array and expression is an array
        if (varType != null && varType.isArray() && expressionType != null && expressionType.isArray()) {
            if (varType.getName().equals(expression.getDescendants().get(1).get("name")))
                return null;
        }

        // if var is an object and expression is an object
        if (expressionType != null && expressionType.getName().equals("Object")) {
            if (varType != null && varType.getName().equals(expression.getChildren().getFirst().get("name")))
                return null;
        }


        // if var is a superclass of expression
        if (varType != null &&  superClass != null && table.getSuper().equals(varType.getName()))
            return null;

        for (String imports : table.getImports()) {
            if ( varType != null && imports.contains(varType.getName()))
                for (String imports2 : table.getImports()) {
                    if (expressionType != null && imports2.contains(expressionType.getName()))
                        return null;
                }
        }

        System.out.println("EXPRESSION VAR :" + expression.get("var"));

        // When a static method is called, we should not check the type of the variable
        if(expression.hasAttribute("var") && typeUtils.isTypeInImports(expression.get("var"), table.getImports())) {
            return null;
        }

        // if the expression is a varRefExpr and the varRefExpr is not in the imports
        if (expression.hasAttribute("var") && (typeUtils.getVarType(expression.get("var")).getName().equals(currentClass) || (superClass != null && typeUtils.getVarType(expression.get("var")).getName().equals(superClass)) || typeUtils.isTypeInImports(typeUtils.getVarType(expression.get("var")).getName(), table.getImports()))) {
            return null;
        }

        String message = "Incompatible types in assignment statement. Variable type: " + varType + ", Expression type: " + expressionType;
        addReport(Report.newError(
                Stage.SEMANTIC,
                assignStmt.getLine(),
                assignStmt.getColumn(),
                message,
                null)
        );

        return null;
    }

}