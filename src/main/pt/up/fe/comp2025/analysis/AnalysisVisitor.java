package pt.up.fe.comp2025.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of AnalysisPass that automatically visits nodes using preorder traversal.
 */
public abstract class AnalysisVisitor extends PreorderJmmVisitor<SymbolTable, Void> implements AnalysisPass {

    private List<Report> reports;

    public AnalysisVisitor() {
        reports = new ArrayList<>();
        setDefaultValue(() -> null);
    }

    protected void addReport(Report report) {
        reports.add(report);
    }

    protected List<Report> getReports() {
        return reports;
    }


    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        // Visit the node
        visit(root, table);

        // Return reports
        return getReports();
    }

    public Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public Type getOperandType(JmmNode node, SymbolTable table, String currentMethod) {
        if (node.getKind().equals(Kind.VAR_REF_EXPR.toString())) return getVariableType(node.get("name"), table, currentMethod);
        else if (node.getKind().equals("IntegerLiteral")) return TypeUtils.newIntType();
        else if (node.getKind().equals("BooleanLiteral")) return new Type("boolean", false);
        else if (node.getKind().equals("StringLiteral")) return new Type("String", false);
        else if (node.getKind().equals("NewArray")) return new Type("Array", true);
        else if (node.getKind().equals("ObjectNew")) return new Type("Object", false);
        else if (node.getKind().equals("ArrayInit")) return new Type("ArrayInit", true);
        else if (node.getKind().equals("This")) return new Type("this", false);
        return getOperandType(node.getChildren().getFirst(), table, currentMethod);
    }

    public Type getVariableType(String varName, SymbolTable table, String currentMethod) {
        System.out.println(table);
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
