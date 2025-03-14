package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;


public class IncompatibleReturnType extends AnalysisVisitor
{
    String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitReturnStmt(JmmNode stmt, SymbolTable table) {
        Type returnType = getOperandType(stmt.getChild(0), table, currentMethod);
        Type methodType = table.getReturnType(currentMethod);

        if (returnType.equals(methodType)) return null;

        String message = "The expected return type is different from the used one.";

        addReport(Report.newError(
                Stage.SEMANTIC,
                stmt.getLine(),
                stmt.getColumn(),
                message,
                null)
        );;
        return null;
    }

}
