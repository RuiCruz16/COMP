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

    private Type getVariableType(String varName, SymbolTable table) {
        for(Symbol s: table.getLocalVariables(currentMethod)) {
            if (s.getName().equals(varName)) {
                return s.getType();
            }
        }

        for(Symbol s: table.getFields()) {
            if (s.getName().equals(varName)) {
                return s.getType();
            }
        }

        for(Symbol s: table.getParameters(currentMethod)) {
            if (s.getName().equals(varName)) {
                return s.getType();
            }
        }

        return null;
    }

    private Type getOperandType(JmmNode node, SymbolTable table) {
        if (node.getKind().equals(Kind.VAR_REF_EXPR.toString())) return getVariableType(node.get("name"), table);
        else if (node.getKind().equals("IntegerLiteral")) return TypeUtils.newIntType();
        else if (node.getKind().equals("BooleanLiteral")) return new Type("bool", false);
        else if (node.getKind().equals("StringLiteral")) return new Type("String", false);
        return getOperandType(node.getChildren().getFirst(), table);
    }

    private Void visitBinaryExpr(JmmNode BinaryOp, SymbolTable table) {

        JmmNode firstOperand = BinaryOp.getChildren().get(0);
        JmmNode secondOperand = BinaryOp.getChildren().get(1);

        Type typeFirstOperand = getOperandType(firstOperand, table);;
        Type typeSecondOperand = getOperandType(secondOperand, table);

        boolean arrayOp = typeFirstOperand.isArray() || typeSecondOperand.isArray();

        // if types are equal there are no incompatible
        if (typeFirstOperand.equals(typeSecondOperand) && !arrayOp) {
            return null;
        }

        // TODO, cases when we use imports!!!

        String message = arrayOp ? "Arrays cannot be used in arithmetic operations."
                : "Incompatible types in binary operation" + BinaryOp +". Operands must be of the same type.";

        // Create error report
        addReport(Report.newError(
                Stage.SEMANTIC,
                BinaryOp.getLine(),
                BinaryOp.getColumn(),
                message,
                null)
        );

        return null;
    }

}
