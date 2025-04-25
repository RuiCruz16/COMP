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

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {

        if (semanticsResult.getConfig().get("optimize") == null) {
            return semanticsResult;
        }

        boolean constantPropagationModified = true;

        while (constantPropagationModified) {
            ConstantPropagationVisitor constantPropagationVisitor = new ConstantPropagationVisitor();
            constantPropagationVisitor.constantFoldingMethod(semanticsResult);

            constantPropagationModified = constantPropagationVisitor.isConstantPropagationModified();
        }

        boolean constantFoldingModified = true;

        while (constantFoldingModified) {
            ConstantFoldingVisitor constantFoldingVisitor = new ConstantFoldingVisitor();
            constantFoldingVisitor.constantFoldingMethod(semanticsResult);

            constantFoldingModified = constantFoldingVisitor.isConstantFoldingModified();
        }

        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        //TODO: Do your OLLIR-based optimizations here

        return ollirResult;
    }


}
