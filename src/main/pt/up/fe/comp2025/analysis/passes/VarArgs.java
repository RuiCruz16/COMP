package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

public class VarArgs extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.CALL_METHOD, this::checkVarArgsCallMethod);
        addVisit("ObjectMethod", this::checkVarArgsObjectMethod);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void checkVarArgsCallMethod(JmmNode vararg, SymbolTable table) {

        if(!(vararg.getKind().equals(Kind.CALL_METHOD.toString()) && vararg.getKind().equals("ObjectMethod")) || !vararg.getChild(0).getKind().equals(Kind.THIS.toString())) return null;

        String methodCalled = vararg.getChildren().getLast().get("name");
        var params = vararg.getChildren("MethodCall").getFirst().getChildren();

        String typeName = table.getParameters(methodCalled).getFirst().getType().getName();
        Type typeLit = new Type(typeName, false);
        for(JmmNode param : params) {

            if(!typeLit.equals(getOperandType(param, table, currentMethod))) {
                String message = "Varargs type mismatch";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        vararg.getLine(),
                        vararg.getColumn(),
                        message,
                        null)
                );
            }

        }
        return null;
    }

    private Void checkVarArgsObjectMethod(JmmNode vararg, SymbolTable table) {

        String methodCalled = vararg.get("suffix");
        var params = vararg.getChildren();

        if(!table.getMethods().contains(methodCalled) || table.getParameters(methodCalled).isEmpty()) return null;

        String typeName = table.getParameters(methodCalled).getFirst().getType().getName();
        Type typeLit = new Type(typeName, false);
        for(JmmNode param : params) {

            if(!typeLit.equals(getOperandType(param, table, currentMethod))) {
                String message = "Varargs type mismatch";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        vararg.getLine(),
                        vararg.getColumn(),
                        message,
                        null)
                );
            }

        }
        return null;
    }

}
