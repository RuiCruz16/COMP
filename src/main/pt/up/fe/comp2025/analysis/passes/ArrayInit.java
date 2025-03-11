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

import java.util.ArrayList;
import java.util.List;

public class ArrayInit extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitArrayInit);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }


    private Void visitArrayInit(JmmNode arrayNode, SymbolTable table) {
        String arrayVarName = arrayNode.getChild(0).get("name");
        Type varType = getOperandType(arrayNode.getChild(0), table, currentMethod);

        if(!arrayNode.getChild(1).getKind().equals(Kind.ARRAY_INIT)) return null;

        var initArray =  arrayNode.getChild(1).getChild(0).getChildren();
        for(JmmNode node : initArray){
            Type nodeType = getOperandType(node, table, currentMethod);
            if(!varType.getName().equals(nodeType.getName())){
                String message = "Array " + arrayVarName + " is not of the same type as the elements being assigned.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        message,
                        null)
                );
            }
        }
        return null;
    }
}
