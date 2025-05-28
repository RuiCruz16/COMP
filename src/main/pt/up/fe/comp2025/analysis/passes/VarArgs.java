package pt.up.fe.comp2025.analysis.passes;

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
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        if(!(vararg.getKind().equals(Kind.CALL_METHOD.toString()) && vararg.getKind().equals("ObjectMethod")) || !vararg.getChild(0).getKind().equals(Kind.THIS.toString())) return null;

        String methodCalled = vararg.getChildren().getLast().get("name");
        var params = vararg.getChildren("MethodCall").getFirst().getChildren();

        String typeName = table.getParameters(methodCalled).getFirst().getType().getName();
        Type typeLit = new Type(typeName, false);
        for(JmmNode param : params) {
            if(!typeLit.equals(typeUtils.getExprType(param))) {
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
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        String methodCalled = vararg.get("suffix");
        var params = vararg.getChildren();

        if(!(table.getMethods().contains(methodCalled) && typeUtils.getVarType(vararg.get("var")).getName().equals(table.getClassName())) || (table.getParameters(methodCalled).isEmpty() && params.isEmpty())) return null;

        if (!table.getParameters(methodCalled).isEmpty()) {
            String typeName = table.getParameters(methodCalled).getFirst().getType().getName();
            Type typeLit = new Type(typeName, false);
            int i = 0;
            for (JmmNode param : params) {
                Type paramType = typeUtils.getExprType(param);
                if(paramType == null) continue;
                if (!typeLit.equals(paramType) && !(paramType.isArray() && paramType.getName().equals(typeName))) {
                    String message = "Object method called with incompatible arguments";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            vararg.getLine(),
                            vararg.getColumn(),
                            message,
                            null)
                    );
                }
                else {
                    if(i + 1 < table.getParameters(methodCalled).size()) i++;

                    typeName = table.getParameters(methodCalled).get(i).getType().getName();
                    typeLit = new Type(typeName, false);

                }
            }
        }

        for (JmmNode child : methodDecl.getParent().getChildren()) {
            if (child.getKind().equals("ExtendsClause")) continue;
            if (child.get("name").equals(methodCalled) && !table.getParameters(methodCalled).isEmpty() ) {
                try {
                    JmmNode parameters = child.getChildren("ParameterList").getFirst();
                    if (!parameters.getChildren().getLast().getChildren().getLast().getChildren("Type").getFirst().getChildren().getFirst().getChildren("VarArgsSuffix").isEmpty())
                        return null;
                } catch (Exception e) {
                    continue;
                }

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
