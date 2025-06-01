package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        //System.out.println(ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {

        if (semanticsResult.getConfig().get("optimize") == null) {
            return semanticsResult;
        }

        boolean madeChanges = true;
        ConstantPropagationVisitor constantPropagationVisitor = new ConstantPropagationVisitor();
        ConstantFoldingVisitor constantFoldingVisitor = new ConstantFoldingVisitor();

        while (madeChanges) {
            constantPropagationVisitor.constantFoldingMethod(semanticsResult);
            boolean propagationChanges = constantPropagationVisitor.isConstantPropagationModified();

            constantFoldingVisitor.constantFoldingMethod(semanticsResult);
            boolean foldingChanges = constantFoldingVisitor.isConstantFoldingModified();

            madeChanges = propagationChanges || foldingChanges;
        }

        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        if (ollirResult.getConfig().get("registerAllocation") == null || ollirResult.getConfig().get("registerAllocation").equals("-1")) return ollirResult;

        RegisterAllocationVisitor registerAllocationVisitor = new RegisterAllocationVisitor(ollirResult, ollirResult.getConfig().get("registerAllocation"));

        registerAllocationVisitor.optimizeRegisterAllocation();

        return ollirResult;
    }


}
