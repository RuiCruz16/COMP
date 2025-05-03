package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private boolean isAfter(JmmNode node1, JmmNode node2) {
        if (!haveSameParentChain(node1, node2)) {
            return false;
        }

        int line1 = node1.getLine();
        int line2 = node2.getLine();

        if (line1 == line2) {
            return node1.getColumn() > node2.getColumn();
        }

        return line1 > line2;
    }

    private boolean haveSameParentChain(JmmNode node1, JmmNode node2) {
        JmmNode method1 = node1.getAncestor(Kind.METHOD_DECL.toString()).orElse(null);
        JmmNode method2 = node2.getAncestor(Kind.METHOD_DECL.toString()).orElse(null);
        return method1 != null && method1 == method2;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {

        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varRefExpr.get("name");


        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        for(var child : varRefExpr.getAncestor(Kind.METHOD_DECL.toString()).get().getChildren(Kind.VAR_DECL.toString())){
            // it means a var is referenced before being declared
            if(child.get("name").equals(varRefName) && isAfter(child, varRefExpr)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        varRefExpr.getLine(),
                        varRefExpr.getColumn(),
                        "Variable '" + varRefName + "' is referenced before being declared",
                        null)
                );
            }
        }

        // Var is a declared variable, return
        if (table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDecl -> varDecl.getName().equals(varRefName))) {
            return null;
        }

        // Var is a class field, return
        // if current method is not main, as static method cannot access class fields
        if(currentMethod.equals("main") && table.getFields().stream()
                .anyMatch(field -> field.getName().equals(varRefName))) {
            var message = String.format("Variable '%s' cannot be accessed in a static method.", varRefName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    varRefExpr.getLine(),
                    varRefExpr.getColumn(),
                    message,
                    null)
            );
        }

        if (table.getFields().stream()
                .anyMatch(field -> field.getName().equals(varRefName))) {
            return null;
        }

        // Var is an imported class, return
        if (table.getImports().stream()
                .anyMatch(importName -> importName.equals(varRefName)))  {
            return null;
        }

        // Create error report
        var message = String.format("Variable '%s' does not exist.", varRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                varRefExpr.getLine(),
                varRefExpr.getColumn(),
                message,
                null)
        );

        return null;
    }


}
