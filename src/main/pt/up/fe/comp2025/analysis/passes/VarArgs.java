package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

public class VarArgs extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.CALL_METHOD, this::checkVarArgs);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void checkVarArgs(JmmNode vararg, SymbolTable table) {
        System.out.println("METHOD HERE");
        System.out.println(vararg.toString());
        return null;
    }

}
