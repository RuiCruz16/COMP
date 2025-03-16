package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.Objects;

public class CallToMethod extends AnalysisVisitor {

    String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.CALL_METHOD, this::visitCallMethod);
        addVisit("ObjectMethod", this::visitCallObjectMethod);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitCallMethod(JmmNode callMethod, SymbolTable table) {
        String methodName = callMethod.getChildren().get(1).get("name");

        if (table.getMethods().contains(methodName)) {
            return null;
        }

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);
        Type variableType = typeUtils.getExprType(callMethod);

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

    private Void visitCallObjectMethod(JmmNode callMethod, SymbolTable table) {

        String methodName = callMethod.get("suffix");
        String varName = callMethod.get("var");
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);
        Type varType = typeUtils.getVarType(varName);

        if (varType == null && !table.getImports().isEmpty()) return null;

        if(varType != null) {
            if (varType.getName().equals(table.getClassName()) && table.getSuper() != null) {
                return null;
            }

            for (String imports : table.getImports()) {
                if (imports.contains(varType.getName())) {
                    return null;
                }
            }
        }

        if (!table.getMethods().contains(methodName)) {
            String message = "Method " + methodName + " is not declared.";
            addReport(Report.newError(Stage.SEMANTIC,
                    callMethod.getLine(),
                    callMethod.getColumn(),
                    message,
                    null
                )
            );
            return null;
        }
        return null;
    }

}
