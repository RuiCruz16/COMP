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

import java.util.ArrayList;
import java.util.List;

public class ArrayAccess extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }


    private Void visitArrayAccess(JmmNode array, SymbolTable table) {
        JmmNode arrayName = array.getChildren().get(0);
        JmmNode arrayIndex = array.getChildren().get(1);

        Type arraySymbol = getOperandType(arrayName, table);
        Type arrayIndexSymbol = getOperandType(arrayIndex, table);


        System.out.println("Current Method: " + currentMethod);

        System.out.println("Array Symbol: " + arraySymbol);
        System.out.println("Array Index Symbol: " + arrayIndexSymbol);

        if (arraySymbol != null && arraySymbol.isArray()) {
            if (arrayIndexSymbol != null && !arrayIndexSymbol.equals(TypeUtils.newIntType())) {
                String message = "Array index must be an integer.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        arrayIndex.getLine(),
                        arrayIndex.getColumn(),
                        message,
                        null)
                );
            }
        } else {
            String message = "Variable " + arrayName.get("name") + " is not an array.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    array.getLine(),
                    array.getColumn(),
                    message,
                    null)
            );
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

}
