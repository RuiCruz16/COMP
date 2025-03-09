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

        Type varType = getOperandType(varRefExpr, table, currentMethod);
        Type expressionType = getOperandType(expression, table, currentMethod);

        if (varType != null && varType.equals(expressionType)) {
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
