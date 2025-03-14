package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

public class CallToMethod extends AnalysisVisitor {

    String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.CALL_METHOD, this::visitCallMethod);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitCallMethod(JmmNode callMethod, SymbolTable table) {
        String methodName = callMethod.getChildren().get(1).get("name");

        System.out.println("Calling method 1 " + methodName);

        if (table.getMethods().contains(methodName)) {
            return null;
        }
        System.out.println("Calling method " + methodName);
        Type variableType = getOperandType(callMethod, table, currentMethod);

        if (variableType.getName().equals(table.getClassName()) && table.getSuper() != null) {
            return null;
        }

        for (String imports : table.getImports()) {
            if (imports.contains(variableType.getName())) {
                return null;
            }
        }

        String message = "Method " + callMethod.getChildren().get(1).get("name") + " is not declared.";

        addReport(Report.newError(
                Stage.SEMANTIC,
                callMethod.getLine(),
                callMethod.getColumn(),
                message,
                null)
        );

        return null;
    }

}
