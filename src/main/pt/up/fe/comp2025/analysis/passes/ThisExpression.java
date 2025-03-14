package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

public class ThisExpression extends AnalysisVisitor {

    String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.THIS, this::visitThisStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitThisStmt(JmmNode thisExpr, SymbolTable table) {
        if (currentMethod.equals("main")) {
            String message = "'this' expression cannot be used in a static method";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    thisExpr.getLine(),
                    thisExpr.getColumn(),
                    message,
                    null)
            );

            return null;
        }
        if (thisExpr.getParent().getKind().equals(Kind.CALL_METHOD.toString())) return null;

        Type var = getOperandType(thisExpr.getParent().getChildren().getFirst(), table, currentMethod);

        if (var != null && (var.getName().equals(table.getClassName()) || var.getName().equals(table.getSuper()))) {
            return null;
        }

        String message = "Invalid use of 'this' expression: 'this' can only be assigned to a variable of the same class type or a superclass";
        addReport(Report.newError(
                Stage.SEMANTIC,
                thisExpr.getLine(),
                thisExpr.getColumn(),
                message,
                null)
        );

        return null;
    }
}
