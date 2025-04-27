package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;


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
        String arrayName;
        Type arraySymbol;
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        if (array.getChild(0).getKind().equals(Kind.ARRAY_INIT.toString())) {
            arrayName = "ArrayInit";
            for (JmmNode arrayLit: array.getChild(0).getChild(0).getChildren()) {
                if (arrayLit.getKind().equals(Kind.OBJECT_METHOD.toString()) && !arrayLit.get("var").equals("this")) {
                    String message = "Array literal cannot be an imported call.";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            arrayLit.getLine(),
                            arrayLit.getColumn(),
                            message,
                            null)
                    );
                    return null;
                }
                if (!typeUtils.getExprType(arrayLit).getName().equals("int")) {
                    String message = "Array literal must be of type int.";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            arrayLit.getLine(),
                            arrayLit.getColumn(),
                            message,
                            null)
                    );
                    return null;
                }
            }
            arraySymbol = new Type("int",true);
        } else {
            arrayName = array.getChildren(Kind.VAR_REF_EXPR.toString()).getFirst().get("name");
            arraySymbol = typeUtils.getVarType(arrayName);
        }

        JmmNode arrayIndex = array.getChild(1);
        Type arrayIndexSymbol = typeUtils.getExprType(arrayIndex);

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
            String message = "Variable " + arrayName + " is not an array.";
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
