package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2025.ast.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConstantFoldingVisitor {

    private boolean constantFoldingModified;

    public void constantFoldingMethod(JmmSemanticsResult semanticsResult) {
        constantFoldingModified = false;

        List<JmmNode> operationsList = new ArrayList<>();

        searchForOperations( semanticsResult.getRootNode(), operationsList);

        for (JmmNode node : operationsList) {
            optimizeWithConstantFolding(node);
        }

        //System.out.println(semanticsResult.getRootNode().toTree());
    }

    private void searchForOperations(JmmNode rootNode, List<JmmNode> operationsList) {
        if (rootNode.getKind().equals(Kind.BINARY_EXPR.toString())) {
            JmmNode leftChild = rootNode.getChildren().get(0);
            JmmNode rightChild = rootNode.getChildren().get(1);

            if (leftChild.getKind().equals(Kind.INTEGER_LITERAL.toString()) && rightChild.getKind().equals(Kind.INTEGER_LITERAL.toString())) {
                operationsList.add(rootNode);
            }

        }

        for (JmmNode child : rootNode.getChildren()) {
            searchForOperations(child, operationsList);
        }
    }

    private void optimizeWithConstantFolding(JmmNode node) {
        JmmNode leftChild = node.getChildren().get(0);
        JmmNode rightChild = node.getChildren().get(1);

        if (leftChild.getKind().equals(Kind.INTEGER_LITERAL.toString()) && rightChild.getKind().equals(Kind.INTEGER_LITERAL.toString()) && node.getKind().equals(Kind.BINARY_EXPR.toString())) {
            String nodeOperation = node.get("op");
            int leftValue = Integer.parseInt(leftChild.get("value"));
            int rightValue = Integer.parseInt(rightChild.get("value"));

            int result = switch (nodeOperation) {
                case "+" -> leftValue + rightValue;
                case "-" -> leftValue - rightValue;
                case "*" -> leftValue * rightValue;
                case "/" -> leftValue / rightValue;
                case "<" -> leftValue < rightValue ? 1 : 0; // TODO: o resultado aparece 1/0.i32 quando deve ser 1/0.bool
                default -> throw new IllegalStateException("Unexpected operation: " + nodeOperation);
            };

            System.out.println("RESULT: " + result);
            JmmNode newNode = new JmmNodeImpl(Collections.singletonList(nodeOperation.equals("<") ? "BooleanLiteral" : Kind.INTEGER_LITERAL.toString()));
            newNode.put("value", String.valueOf(result));
            System.out.println("JMM node: " + newNode);
            int oldNodeIndex = node.getIndexOfSelf();
            JmmNode oldNodeParent = node.getParent();

            oldNodeParent.removeChild(oldNodeIndex);
            oldNodeParent.add(newNode, oldNodeIndex);

            constantFoldingModified = true;
        }
    }

    public boolean isConstantFoldingModified() {
        return constantFoldingModified;
    }
}
