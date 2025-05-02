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

import java.util.List;


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

    private List<JmmNode> getArguments(JmmNode callMethod) {

        JmmNode methodCallNode = callMethod.getChildren().stream()
                .filter(child -> "MethodCall".equals(child.getKind()))
                .findFirst()
                .orElse(null);

        if (methodCallNode == null) {
            return List.of();
        }


        return methodCallNode.getChildren();
    }

    private Void visitCallMethod(JmmNode callMethod, SymbolTable table) {

        String methodName = callMethod.getChildren().get(1).get("name");
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        if (!table.getMethods().contains(methodName) && callMethod.getChild(0).getChild(0).get("name").equals(table.getClassName())) {
            String message = "Method " + methodName + " is not declaredd.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    callMethod.getLine(),
                    callMethod.getColumn(),
                    message,
                    null
            ));
            return null;
        }

        Type variableType = typeUtils.getExprType(callMethod);
        if (variableType == null) return null;

        if (variableType.getName().equals(table.getClassName()) && table.getSuper() != null) {
            return null;
        }

        for (String imports : table.getImports()) {
            if (imports.contains(variableType.getName())) {
                return null;
            }
        }
        List<Symbol> expectedParams = table.getParameters(methodName);

        List<JmmNode> arguments = getArguments(callMethod);

        try {
            if(hasVarArgs(callMethod, table, methodName)) {
                return null;
            }
        } catch (Exception ignored) { }


        if (expectedParams.size() != arguments.size()) {
            String message = "Method " + methodName + " expects " + expectedParams.size()
                    + " arguments, but " + arguments.size() + " were provided.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    callMethod.getLine(),
                    callMethod.getColumn(),
                    message,
                    null
            ));
            return null;
        }

        for (int i = 0; i < arguments.size(); i++) {
            JmmNode argument = arguments.get(i);
            Type expectedType = expectedParams.get(i).getType();

            Type actualType = typeUtils.getExprType(argument);

            if (!expectedType.equals(actualType)) {
                String message = "Argument " + (i + 1) + " of method " + methodName
                        + " expects type " + expectedType.getName()
                        + (expectedType.isArray() ? "[]" : "")
                        + ", but type " + actualType.getName()
                        + (actualType.isArray() ? "[]" : "") + " was provided.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        argument.getLine(),
                        argument.getColumn(),
                        message,
                        null
                ));
            }
        }

        return null;

    }

    private Void visitCallObjectMethod(JmmNode callMethod, SymbolTable table) {

        String methodName = callMethod.get("suffix");
        String varName = callMethod.get("var");

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        Type varType = typeUtils.getVarType(varName);
        if (varType == null && !table.getImports().isEmpty()) return null;


        if (varType != null && varType.getName().equals(table.getClassName()) && table.getSuper() != null) {
                return null;
        }

        for (String imports : table.getImports()) {
            if (varType != null && imports.contains(varType.getName())) {
                return null;
            }
        }


        if (!table.getMethods().contains(methodName)) {
            String message = "Method " + methodName + " is not declared.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    callMethod.getLine(),
                    callMethod.getColumn(),
                    message,
                    null
            ));
            return null;
        }

        List<Symbol> expectedParams = table.getParameters(methodName);

        List<JmmNode> arguments = callMethod.getChildren();

        try {
            if(hasVarArgs(callMethod, table, methodName)) {
                return null;
            }
        } catch (Exception ignored) { }

        if (expectedParams.size() != arguments.size()) {
            String message = "Method " + methodName + " expects " + expectedParams.size()
                    + " arguments, but " + arguments.size() + " were provided.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    callMethod.getLine(),
                    callMethod.getColumn(),
                    message,
                    null
            ));
            return null;
        }

        for (int i = 0; i < arguments.size(); i++) {
            JmmNode argument = arguments.get(i);
            Type expectedType = expectedParams.get(i).getType();

            Type actualType = typeUtils.getExprType(argument);

            if (actualType == null) {
                String message = "Could not resolve the type for argument " + (i + 1)
                        + " of method " + methodName + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        argument.getLine(),
                        argument.getColumn(),
                        message,
                        null
                ));
                continue;
            }

            if (!expectedType.equals(actualType)) {
                String message = "Argument " + (i + 1) + " of method " + methodName
                        + " expects type " + expectedType.getName()
                        + (expectedType.isArray() ? "[]" : "")
                        + ", but type " + actualType.getName()
                        + (actualType.isArray() ? "[]" : "") + " was provided.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        argument.getLine(),
                        argument.getColumn(),
                        message,
                        null
                ));
            }
        }

        return null;
    }

    private boolean hasVarArgs(JmmNode node, SymbolTable table, String methodCalled) throws Exception {
        JmmNode methodDecl = null;
        JmmNode root = node.getAncestor(Kind.PROGRAM.toString()).get();
        for(JmmNode method : root.getDescendants(Kind.METHOD_DECL.toString())) {
            if(method.get("name").equals(methodCalled)) {
                methodDecl = method;
                break;
            }
        }

        if(methodDecl == null) return false;
        for (JmmNode child : methodDecl.getParent().getChildren()) {
            if (child.getKind().equals("ExtendsClause")) continue;
            if (child.get("name").equals(methodCalled) && !table.getParameters(methodCalled).isEmpty() ) {
                try {
                    JmmNode parameters = child.getChildren("ParameterList").getFirst();
                    if (!parameters.getChildren().getLast().getChildren().getLast().getChildren("Type").getFirst().getChildren().getFirst().getChildren("VarArgsSuffix").isEmpty())
                        return true;
                } catch (Exception e) {
                    continue;
                }

            }
        }
        return false;
    }
}
