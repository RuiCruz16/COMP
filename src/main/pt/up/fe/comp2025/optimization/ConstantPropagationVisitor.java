package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2025.ast.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConstantPropagationVisitor {
    private boolean constantPropagationModified =  false;

    public void constantFoldingMethod(JmmSemanticsResult semanticsResult) {
        constantPropagationModified = false;

        List<JmmNode> operationsList = new ArrayList<>();

        searchForOperations( semanticsResult.getRootNode(), operationsList);

        for (JmmNode node : operationsList) {
            optimizeWithConstantPropagation(semanticsResult.getRootNode(), node);
        }

        //System.out.println(semanticsResult.getRootNode().toTree());
    }

    private boolean isAfter(JmmNode assignNode, JmmNode referenceNode) {
        JmmNode current = assignNode;

        while (current != null) {
            if (current == referenceNode) return true;
            current = current.getParent();
        }

        return false;
    }



    private JmmNode getConstantAssignNode(JmmNode root, JmmNode node, String variableName) {

        for (JmmNode assignNode : root.getDescendants(Kind.ASSIGN_STMT)) {
            if (isAfter(assignNode, node)) continue;

            List<JmmNode> varRefs = assignNode.getChildren(Kind.VAR_REF_EXPR);

            if (!varRefs.isEmpty()) {
                JmmNode varRef = varRefs.getFirst();
                if (variableName.equals(varRef.get("name"))) {

                    if(assignNode.getChildren().getLast().getKind().equals(Kind.INTEGER_LITERAL.toString()) ||
                            assignNode.getChildren().getLast().getKind().equals("BooleanLiteral"))
                    {
                        return assignNode;
                    }

                }
            }
        }

        return null;
    }


    private void searchForOperations(JmmNode rootNode, List<JmmNode> operationsList) {
        if (rootNode.getKind().equals(Kind.BINARY_EXPR.toString())) {
            JmmNode leftChild = rootNode.getChildren().get(0);
            JmmNode rightChild = rootNode.getChildren().get(1);

            if (leftChild.getKind().equals(Kind.VAR_REF_EXPR.toString()) || rightChild.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
                operationsList.add(rootNode);
            }

        }

        if (rootNode.getKind().equals(Kind.RETURN_STMT.toString())) {

            if (!rootNode.getChildren(Kind.VAR_REF_EXPR).isEmpty()) {
                operationsList.add(rootNode);
            }

        }

        for (JmmNode child : rootNode.getChildren()) {
            searchForOperations(child, operationsList);
        }
    }

    private void optimizeWithConstantPropagation(JmmNode root, JmmNode node) {

        for(JmmNode child : node.getChildren()) {
            propagate(root, node, child);
        }

    }

    private boolean isVariableChanged(JmmNode node, String variableName) {

        if (node.getKind().equals(Kind.ASSIGN_STMT.toString())) {
            List<JmmNode> varRefs = node.getChildren(Kind.VAR_REF_EXPR);
            if (!varRefs.isEmpty() && varRefs.getFirst().get("name").equals(variableName)) {
                return true;
            }
        }

        for (JmmNode child : node.getChildren()) {
            if (isVariableChanged(child, variableName)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkWhileVarChange(JmmNode root, JmmNode node, JmmNode child) {

        List<JmmNode> allWhileNodes = root.getDescendants(Kind.WHILE);

        for (JmmNode whileNode : allWhileNodes) {
            if (isAfter(whileNode, node)) continue;

            List<JmmNode> whileBody = whileNode.getChildren();
            for (JmmNode bodyNode : whileBody) {
                if (isVariableChanged(bodyNode, child.get("name"))) {
                    return true;
                }
            }
        }

        return false;
    }

    private void propagate(JmmNode root, JmmNode node, JmmNode child) {

        if(child.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
            if(checkWhileVarChange(root, node, child)) return;
            var constantNode = getConstantAssignNode(root, node, child.get("name"));

            if(constantNode != null) {
                int oldNodeIndex = child.getIndexOfSelf();
                JmmNode newRightNode = new JmmNodeImpl(Collections.singletonList(constantNode.getChildren().getLast().getKind()));
                newRightNode.put("value", String.valueOf(constantNode.getChildren().getLast().get("value")));
                node.removeChild(oldNodeIndex);
                node.add(newRightNode, oldNodeIndex);

                constantPropagationModified = true;
            }
        }
    }

    public boolean isConstantPropagationModified() {
        return constantPropagationModified;
    }
}
