package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class IncompatibleOperandType extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryOp, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        JmmNode firstOperand = binaryOp.getChildren().get(0);
        JmmNode secondOperand = binaryOp.getChildren().get(1);

        Type typeFirstOperand = typeUtils.getExprType(firstOperand);
        Type typeSecondOperand = typeUtils.getExprType(secondOperand);

        boolean arrayOp = typeFirstOperand.isArray() || typeSecondOperand.isArray();
        Type typeOperation = typeUtils.getExprType(binaryOp);
        String op = binaryOp.get("op");
        boolean isLogicalOperator = op.equals("&&") || op.equals("||") ||  op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
        boolean isBooleanOperator = op.equals("&&") || op.equals("||");
        // if types are equal they are no incompatible

        if ((typeFirstOperand.equals(typeSecondOperand) && !typeFirstOperand.getName().equals("boolean")) && !arrayOp && (isLogicalOperator || typeOperation.equals(typeFirstOperand))) {
            return null;
        }

        if(isBooleanOperator && typeOperation.equals(typeFirstOperand) && typeSecondOperand.equals(typeFirstOperand)) {
            return null;
        }

        String message = arrayOp ? "Arrays cannot be used in arithmetic operations."
                : "Incompatible types in binary operation" + binaryOp +".";

        // Create error report
        addReport(Report.newError(
                Stage.SEMANTIC,
                binaryOp.getLine(),
                binaryOp.getColumn(),
                message,
                null)
        );

        return null;
    }

}
