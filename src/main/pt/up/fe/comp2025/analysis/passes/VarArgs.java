package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.PARAM;
import static pt.up.fe.comp2025.ast.Kind.fromString;

public class VarArgs extends AnalysisVisitor {

    private String currentMethod;
    private JmmNode methodDecl;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.CALL_METHOD, this::checkVarArgsCallMethod);
        addVisit(Kind.OBJECT_METHOD, this::checkVarArgsObjectMethod);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        methodDecl = method;
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
                String message = "Method call with incompatible arguments";
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
        System.out.println("methodCalled: " + methodCalled);
        var params = vararg.getChildren();

        System.out.println("params: " + params);
        System.out.println("Method params " + table.getParameters(methodCalled));

        if(!table.getMethods().contains(methodCalled) || (table.getParameters(methodCalled).isEmpty() && params.isEmpty())) return null;

        if (!table.getParameters(methodCalled).isEmpty()) {
            String typeName = table.getParameters(methodCalled).getFirst().getType().getName();
            Type typeLit = new Type(typeName, false);
            for (JmmNode param : params) {

                if (!typeLit.equals(getOperandType(param, table, currentMethod))) {
                    String message = "Object method called with incompatible arguments";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            vararg.getLine(),
                            vararg.getColumn(),
                            message,
                            null)
                    );
                }

            }
        }

        for (JmmNode child : methodDecl.getParent().getChildren()) {
            if (child.get("name").equals(methodCalled) && !table.getParameters(methodCalled).isEmpty() ) {
                if (child.getChildren().get(1).getChildren().getFirst().get("param").contains("suffix: ..."))
                    return null;
            }
        }

        if(table.getParameters(methodCalled).size() != params.size()) {
            String message = "Method call with wrong number of arguments";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    vararg.getLine(),
                    vararg.getColumn(),
                    message,
                    null)
            );
        }
        return null;
    }

}
