package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;


public class IncompatibleReturnType extends AnalysisVisitor
{
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        int countReturns = method.getDescendants("ReturnStmt").size();

        if (countReturns > 1) {
            String message = "Method '" + currentMethod + "' cannot have more than one return statement";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    method.getLine(),
                    method.getColumn(),
                    message,
                    null)
            );
        }

        if(!table.getReturnType(currentMethod).getName().equals("void")) {
            if (!hasReturnStmt(method)) {
                String message = "Method '" + currentMethod + "' is expected to have a return statement.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        message,
                        null)
                );
            }
        } else {
            if (hasReturnStmt(method)) {
                String message = "Method '" + currentMethod + "' is not expected to have a return statement.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        message,
                        null)
                );
            }
        }        System.out.println("HERER");

        if(method.getChildren(Kind.RETURN_STMT.toString()).isEmpty()) return null;

        JmmNode lastReturnNode = method.getChildren(Kind.RETURN_STMT.toString()).getLast();
        if (lastReturnNode != null && lastReturnNode != method.getChildren().getLast()) {
            String message = "Method '" + currentMethod + "' has statements after the last return statement.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    lastReturnNode.getLine(),
                    lastReturnNode.getColumn(),
                    message,
                    null)
            );
        }

        return null;

    }

    private boolean hasReturnStmt(JmmNode node) {
        for (JmmNode child : node.getChildren()) {
            if(child.toString().equals("ReturnStmt")) {
                return true;
            }
        }
        return false;
    }

    private Void visitReturnStmt(JmmNode stmt, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);
        Type returnType = typeUtils.getExprType(stmt.getChild(0));

        Type methodType = table.getReturnType(currentMethod);

        if (returnType == null || returnType.equals(methodType)) return null;

        String message = "The expected return type is different from the used one.";

        addReport(Report.newError(
                Stage.SEMANTIC,
                stmt.getLine(),
                stmt.getColumn(),
                message,
                null)
        );;
        return null;
    }

}

