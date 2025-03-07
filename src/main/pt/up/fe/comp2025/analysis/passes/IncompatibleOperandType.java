package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

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

    private String getVariableType(JmmNode node, SymbolTable table) {
        if (node.getKind().equals("VarRefExpr")) {
            for (Symbol s : table.getLocalVariables(currentMethod)) {
                if (s.getName().equals(node.get("name"))) {
                    return s.getType().getName();
                }
            }
            for (Symbol s : table.getFields()) {
                if (s.getName().equals(node.get("name"))) {
                    return s.getType().getName();
                }
            }
        }

        return null;
    }

    private Void visitBinaryExpr(JmmNode BinaryOp, SymbolTable table) {

        JmmNode firstOperand = BinaryOp.getChildren().get(0);
        JmmNode secondOperand = BinaryOp.getChildren().get(1);
        String typeFirstOperand = null;
        String typeSecondOperand = null;

        typeFirstOperand = getVariableType(firstOperand, table);

        typeSecondOperand = getVariableType(secondOperand, table);


        System.out.println("TYPEEEEE");
        System.out.println(typeFirstOperand + " " + typeSecondOperand);
        System.out.println("--------------------");

        System.out.println("BINARY OPERATOR");
        System.out.println(BinaryOp.getChildren());
        System.out.println(firstOperand.getKind() + " = " + secondOperand);

        System.out.println("----");
        System.out.println(table.getLocalVariables(currentMethod));

        if (firstOperand.getKind().equals("VarRefExpr")) {
            System.out.println("HEEREEEE");
            System.out.println(firstOperand.get("name"));
            System.out.println(table.getLocalVariables(currentMethod));
            for (Symbol s : table.getLocalVariables(currentMethod)) {
                if (s.getName().equals(firstOperand.get("name"))) {
                    System.out.println("CRLH 1");
                    System.out.println(s.getType().getName());
                }
            }
        }
        else {
            firstOperand.getKind();
        }

        if (secondOperand.getKind().equals("VarRefExpr")) {
            System.out.println("HEEREEEE");
            System.out.println(secondOperand.get("name"));
            System.out.println(table.getLocalVariables(currentMethod));
            for (Symbol s : table.getLocalVariables(currentMethod)) {
                if (s.getName().equals(secondOperand.get("name"))) {
                    System.out.println("CRLH 2");
                    System.out.println(s.getType());
                }
            }
        }

        System.out.println(firstOperand.getKind());

        // Create error report
        var message = String.format("Incompatible types in binary operation '%s'. Operands must be of the same type.", BinaryOp);
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
