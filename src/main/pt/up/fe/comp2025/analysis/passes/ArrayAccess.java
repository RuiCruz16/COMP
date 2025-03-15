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

        Type arraySymbol = getOperandType(arrayName, table, currentMethod);
        Type arrayIndexSymbol = getOperandType(arrayIndex, table, currentMethod);

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
}
