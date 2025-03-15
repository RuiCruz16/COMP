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
        if (node.getKind().equals(Kind.VAR_REF_EXPR.toString()) || node.getKind().equals("Var")) return getVariableType(node.get("name"), table, currentMethod);
        else if (node.getKind().equals("IntegerLiteral") || node.getKind().equals("IntLit")) return TypeUtils.newIntType();
        else if (node.getKind().equals("BooleanLiteral") || node.getKind().equals("BooleanLit")) return new Type("boolean", false);
        else if (node.getKind().equals("StringLiteral") || node.getKind().equals("StringLit")) return new Type("String", false);
        else if (node.getKind().equals("NewArray")) return new Type("Array", true);
        else if (node.getKind().equals("ObjectNew")) return new Type("Object", false);
        else if (node.getKind().equals("ArrayInit")) return new Type("ArrayInit", true);
        else if (node.getKind().equals("ObjectAccess")) {
            return new Type("ObjectAccess", true);
        }
        else if (node.getKind().equals("This")) {
            return new Type("this", false);
        }
        else if (node.getKind().equals("CallMethod")) {
            String methodName = node.getChildren("MethodCall").getFirst().get("name");
            return table.getReturnType(methodName);
        }
        else if(node.getKind().equals("ObjectMethod")) {
            String methodName = node.get("suffix");
            if(table.getMethods().contains(methodName)) {
                return  table.getReturnType(methodName);
            }
            return null;
        }
        else if (node.getKind().equals(Kind.ARRAY_ACCESS.toString())) {
            String varName = node.getChildren(Kind.VAR_REF_EXPR.toString()).getFirst().get("name");
            String literalTypeName = getVariableType(varName, table, currentMethod).getName();
            return new Type(literalTypeName, false);
        }
        return getOperandType(node.getChildren().getFirst(), table, currentMethod);
    }

    public Type getVariableType(String varName, SymbolTable table, String currentMethod) {
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
