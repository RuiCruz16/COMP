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


public class CallToMethod extends AnalysisVisitor {

    String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.CALL_METHOD, this::visitCallMethod);
        addVisit(Kind.OBJECT_METHOD, this::visitCallObjectMethod);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        if (currentMethod.equals("main")) {
            if (table.getParameters("main").isEmpty()) {
                String message = "Method " + currentMethod + " must have one parameter.";

                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        message,
                        null)
                );
            } else {
                for (JmmNode param : method.getChild(1).getChild(0).getChildren()) {
                    String message = "Parameter from method " + currentMethod + " must have the type String[].";
                    try {
                        if(!param.getChild(0).getChildren("SuffixPart").isEmpty()) {
                            JmmNode suffixPart = param.getChild(0).getChildren("SuffixPart").getFirst();
                            if (suffixPart.getChildren("ArraySuffix").isEmpty()) {

                                addReport(Report.newError(
                                        Stage.SEMANTIC,
                                        method.getLine(),
                                        method.getColumn(),
                                        message,
                                        null)
                                );
                            }
                        }
                    } catch (Exception e) {

                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                method.getLine(),
                                method.getColumn(),
                                message,
                                null)
                        );
                    }
                }
            }
        }

        return null;
    }

    private Void visitCallMethod(JmmNode callMethod, SymbolTable table) {
        String methodName = callMethod.getChildren().get(1).get("name");

        if (table.getMethods().contains(methodName)) {
            return null;
        }

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);
        Type variableType = typeUtils.getExprType(callMethod);

        if (variableType.getName().equals(table.getClassName()) && table.getSuper() != null) {
            return null;
        }

        for (String imports : table.getImports()) {
            if (imports.contains(variableType.getName())) {
                return null;
            }
        }

        String message = "Method " + callMethod.getChildren().get(1).get("name") + " is not declared.";

        addReport(Report.newError(
                Stage.SEMANTIC,
                callMethod.getLine(),
                callMethod.getColumn(),
                message,
                null)
        );

        checkValidParameters(callMethod, table);

        return null;
    }

    private Void visitCallObjectMethod(JmmNode callMethod, SymbolTable table) {

        String methodName = callMethod.get("suffix");
        String varName = callMethod.get("var");
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);
        Type varType = typeUtils.getVarType(varName);

        if (varType == null && !table.getImports().isEmpty()) return null;

        if(varType != null) {
            if (varType.getName().equals(table.getClassName()) && table.getSuper() != null) {
                return null;
            }

            for (String imports : table.getImports()) {
                if (imports.contains(varType.getName())) {
                    return null;
                }
            }
        }

        if (!table.getMethods().contains(methodName)) {
            String message = "Method " + methodName + " is not declared.";
            addReport(Report.newError(Stage.SEMANTIC,
                    callMethod.getLine(),
                    callMethod.getColumn(),
                    message,
                    null
                )
            );
            return null;
        }

        checkValidParameters(callMethod, table);

        return null;
    }

    private void checkValidParameters(JmmNode callMethod, SymbolTable table) {

        String methodCalled = "";
        if(callMethod.getKind().equals(Kind.OBJECT_METHOD.toString())) {
            if(!callMethod.get("var").equals("this")) return;
            methodCalled = callMethod.get("suffix");
        }

        try {
            if(callMethod.getChildren().size() > 1 && table.getParameters(methodCalled).size() != callMethod.getChild(1).getChildren().getFirst().getChildren().size()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        callMethod.getLine(),
                        callMethod.getColumn(),
                        "Method call with wrong number of parameters.",
                        null)
                );
            }
        } catch (Exception ignored) { }
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);


        if(!callMethod.getChildren().isEmpty()) {
            for(int i = 0; i < table.getParameters(methodCalled).size(); i++) {
                Symbol param = table.getParameters(methodCalled).get(i);
                JmmNode paramNode = callMethod.getChild(i);
                Type paramType = typeUtils.getExprType(paramNode);
                if(!param.getType().equals(paramType)) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            callMethod.getLine(),
                            callMethod.getColumn(),
                            "Parameter from method " + methodCalled + " must have the type " + param.getType().getName() + ".",
                            null)
                    );
                }

            }
        }

    }
}
