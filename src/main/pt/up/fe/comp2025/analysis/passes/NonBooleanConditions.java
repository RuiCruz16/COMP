package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;


public class NonBooleanConditions extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.IF, this::visitLogicalCondition);
        addVisit(Kind.WHILE, this::visitLogicalCondition);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitLogicalCondition(JmmNode ifDecl, SymbolTable table) {

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        Type conditionType = typeUtils.getExprType(ifDecl.getChildren().getFirst());

        if (conditionType != null && conditionType.getName().equals("bool")) {
            return null;
        }

        String message = "Condition must be of type boolean.";
        addReport(Report.newError(
                Stage.SEMANTIC,
                ifDecl.getLine(),
                ifDecl.getColumn(),
                message,
                null)
        );

        return null;
    }

}
