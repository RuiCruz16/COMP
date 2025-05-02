package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


public class RegisterAllocationVisitor {
    OllirResult ollirResult;
    Integer numRegisters;
    Map<Instruction, Set<Element>> usedVars = new HashMap<>();
    Map<Instruction, Set<Element>> definedVars = new HashMap<>();
    Map<Instruction, Set<Element>> liveIn = new HashMap<>();
    Map<Instruction, Set<Element>> liveOut = new HashMap<>();

    public RegisterAllocationVisitor(OllirResult ollirResult, String registerAllocation) {
        this.ollirResult = ollirResult;
        this.numRegisters = Integer.parseInt(registerAllocation);
        for (var method: ollirResult.getOllirClass().getMethods()) {
            method.buildCFG();
            if (method.getInstructions().getFirst().getSuccessors() != null) {
                initializeVariables(method);
                livenessAnalysis(method);
                Map<Element, Integer> colorMap = buildInferenceGraph(method);
                if(colorMap != null) {
                    // iterate over varTable
                    for(Map.Entry<String, Descriptor> var : method.getVarTable().entrySet()) {
                        // iterate over colorMap and check if var is in it
                        for (Map.Entry<Element, Integer> entry : colorMap.entrySet()) {
                            Operand varElement = (Operand) entry.getKey();
                            int register = entry.getValue();

                            if (varElement.getName().equals(var.getKey())) {
                                Descriptor newDescriptor = var.getValue();
                                newDescriptor.setVirtualReg(register);

                                if(checkIfIsField(ollirResult.getOllirClass(), varElement)) {
                                    newDescriptor.setScope(VarScope.FIELD);
                                }
                                else if(checkIfIsParam(method, varElement)) {
                                    newDescriptor.setScope(VarScope.PARAMETER);
                                }
                                else {
                                    newDescriptor.setScope(VarScope.LOCAL);
                                }
                                method.getVarTable().put(varElement.getName(), newDescriptor);
                            }

                        }
                    }
                }
            }
        }

    }

    public boolean checkIfIsParam(Method method, Operand operand) {
        for(Element element : method.getParams()) {
            Operand param = (Operand) element;
            if(param.getName().equals(operand.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean checkIfIsField(ClassUnit classUnit, Operand operand) {
        for(Field field : classUnit.getFields()) {
            field.getFieldName();
            if(field.getFieldName().equals(operand.getName())) {
                return true;
            }
        }
        return false;
    }

    private void initializeVariables(Method method) {
        for (Instruction instruction : method.getInstructions()) {
            usedVars.put(instruction, new HashSet<>());
            definedVars.put(instruction, new HashSet<>());
            liveIn.put(instruction, new HashSet<>());
            liveOut.put(instruction, new HashSet<>());
            trackDefinedVariables(instruction);
            trackUsedVariables(instruction, null);
        }
    }

    private void livenessAnalysis(Method method) {

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Instruction instruction : method.getInstructions().reversed()) {
                Set<Element> auxIn = new HashSet<>(liveIn.get(instruction));
                Set<Element> auxOut = new HashSet<>(liveOut.get(instruction));

                for (Node successor : instruction.getSuccessors()) {
                    if (!successor.getClass().equals(Node.class)) {
                        liveOut.get(instruction).addAll(liveIn.get(successor.toInstruction()));
                    }
                }

                Set<Element> tempOut = new HashSet<>(liveOut.get(instruction));

                for (Element defined : definedVars.get(instruction)) {
                    tempOut.removeIf(elemTempOut -> defined.toString().equals(elemTempOut.toString()));
                }

                liveIn.get(instruction).addAll(tempOut);
                liveIn.get(instruction).addAll(usedVars.get(instruction));

                if (!((auxIn.equals(liveIn.get(instruction))) && auxOut.equals(liveOut.get(instruction)))) {
                    changed = true;
                }
            }
        }
    }

    private void trackDefinedVariables(Instruction instruction) {
        if (instruction.getInstType().equals(InstructionType.ASSIGN)) {
            AssignInstruction assign = (AssignInstruction) instruction;
            definedVars.get(instruction).add(assign.getDest());
        }
        else if (instruction.getInstType().equals(InstructionType.PUTFIELD)) {
            PutFieldInstruction putField = (PutFieldInstruction) instruction;
            definedVars.get(instruction).add(putField.getField());
        }
    }

    private void trackUsedVariables(Instruction instruction, AssignInstruction assignInstruction) {
        switch (instruction.getInstType()) {
            case RETURN:
                usedInReturns((ReturnInstruction) instruction, assignInstruction);
                break;

            case ASSIGN:
                usedInAssigns((AssignInstruction) instruction);
                break;

            case NOPER:
                usedInNopers((SingleOpInstruction) instruction, assignInstruction);
                break;

            case BINARYOPER:
                usedInBinaryOpers((BinaryOpInstruction) instruction, assignInstruction);
                break;

            case UNARYOPER:
                usedInUnaryOpers((UnaryOpInstruction) instruction, assignInstruction);
                break;

            case PUTFIELD:
                usedInPutFields((PutFieldInstruction) instruction, assignInstruction);
                break;

            case GETFIELD:
                usedInGetFields((GetFieldInstruction) instruction, assignInstruction);
                break;

            case CALL:
                usedInCalls((CallInstruction) instruction, assignInstruction);
                break;

            case BRANCH:
                usedInBranches((CondBranchInstruction) instruction, assignInstruction);
                break;

        }
    }

    private void usedInReturns(ReturnInstruction instruction, AssignInstruction assignInstruction) {
        if (assignInstruction != null) {
            if (instruction.hasReturnValue() && instruction.getOperand().isPresent()) {
                usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(instruction.getOperand().get());
            }
        } else {
            if (instruction.hasReturnValue() && instruction.getOperand().isPresent()) {
                usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(instruction.getOperand().get());
            }
        }
    }

    private void usedInAssigns(AssignInstruction instruction) {
        trackUsedVariables(instruction.getRhs(), instruction);
    }

    private void usedInNopers(SingleOpInstruction instruction, AssignInstruction assignInstruction) {

        if (assignInstruction != null) {
            if (!instruction.getSingleOperand().isLiteral()) {
                usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(instruction.getSingleOperand());
            }
        } else {
            if (!instruction.getSingleOperand().isLiteral()) {
                usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(instruction.getSingleOperand());
            }
        }
    }

    private void usedInBinaryOpers(BinaryOpInstruction instruction, AssignInstruction assignInstruction) {

        if (assignInstruction != null) {
            if (!instruction.getOperands().isEmpty()) {
                for (Element operand : instruction.getOperands()) {
                    if (operand.isLiteral()) {
                        continue;
                    }
                    usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(operand);
                }
            }
        } else {
            if (!instruction.getOperands().isEmpty()) {
                for (Element operand : instruction.getOperands()) {
                    if (operand.isLiteral()) {
                        continue;
                    }
                    usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(operand);
                }
            }
        }
    }

    private void usedInUnaryOpers(UnaryOpInstruction instruction, AssignInstruction assignInstruction) {

        if (assignInstruction != null) {
            if (!instruction.getOperand().isLiteral()) {
                usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(instruction.getOperand());
            }
        } else {
            if (!instruction.getOperand().isLiteral()) {
                usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(instruction.getOperand());
            }
        }
    }

    private void usedInPutFields(PutFieldInstruction instruction, AssignInstruction assignInstruction) {

        if (assignInstruction != null) {
            if (!instruction.getValue().isLiteral()) {
                usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(instruction.getValue());
            }
        } else {
            if (!instruction.getValue().isLiteral()) {
                usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(instruction.getValue());
            }
        }
    }

    private void usedInGetFields(GetFieldInstruction instruction, AssignInstruction assignInstruction) {

        if (assignInstruction != null) {
            usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(instruction.getField());
        } else {
            usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(instruction.getField());
        }
    }

    private void usedInCalls(CallInstruction instruction, AssignInstruction assignInstruction) {

        if (assignInstruction != null) {
            if (!instruction.getArguments().isEmpty()) {
                for (Element argument : instruction.getArguments()) {
                    if (argument.isLiteral()) {
                        continue;
                    }
                    usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(argument);
                }
            }
        } else {
            if (!instruction.getArguments().isEmpty()) {
                for (Element argument : instruction.getArguments()) {
                    if (argument.isLiteral()) {
                        continue;
                    }
                    usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(argument);
                }
            }
        }
    }

    private void usedInBranches(CondBranchInstruction instruction, AssignInstruction assignInstruction) {
        if (assignInstruction != null) {
            if (!instruction.getOperands().isEmpty()) {
                for (Element operand : instruction.getOperands()) {
                    if (operand.isLiteral()) {
                        continue;
                    }
                    usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(operand);
                }
            }
        }
        else {
            if (!instruction.getOperands().isEmpty()) {
                for (Element operand : instruction.getOperands()) {
                    if (operand.isLiteral()) {
                        continue;
                    }
                    usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(operand);
                }
            }
        }
    }

    private void addToInterferenceGraph(Map<Element, Set<Element>> interferenceGraph, Element element, Element element2) {
        boolean found = false;

        for (Map.Entry<Element, Set<Element>> entry : interferenceGraph.entrySet()) {
            Element existingElement = entry.getKey();

            if (existingElement.toString().equals(element.toString())) {
                found = true;

                boolean isDuplicate = false;
                for (Element existingInSet : entry.getValue()) {
                    if (existingInSet.toString().equals(element2.toString())) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    entry.getValue().add(element2);
                }
                break;
            }
        }

        if (!found) {
            Set<Element> newSet = new HashSet<>();
            newSet.add(element2);
            interferenceGraph.put(element, newSet);
        }
    }

    public boolean checkIfContainsElement(Set<Element> elements, Element element) {
        for (Element elem : elements) {
            if (elem.toString().equals(element.toString())) {
                return true;
            }
        }
        return false;
    }

    private Map<Element, Integer> buildInferenceGraph(Method method) {
        // represents the node and the set of nodes that interfere with it aka its edges
        Map<Element, Set<Element>> interferenceGraph = new HashMap<>();

        for (Instruction instruction : method.getInstructions()) {
            Set<Element> liveInSet = liveIn.get(instruction);
            Set<Element> liveOutSet = liveOut.get(instruction);
            Set<Element> definedVarsSet = definedVars.get(instruction);
            Set<Element> combinedSet = new HashSet<>();
            combinedSet.addAll(liveInSet);
            combinedSet.addAll(liveOutSet);
            combinedSet.addAll(definedVarsSet);

            for (Element var : combinedSet) {
                boolean exists = false;

                for (Element existingKey : interferenceGraph.keySet()) {
                    if (existingKey.toString().equals(var.toString())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    interferenceGraph.put(var, new HashSet<>());
                }
            }


            for (Element element : combinedSet) {
                for (Element element2 : combinedSet) {
                    if (!element.toString().equals(element2.toString())) {
                        if(!checkIfContainsElement(liveOutSet, element2) && checkIfContainsElement(liveInSet, element2) && !liveOutSet.isEmpty()) {
                            continue;
                        }
                        if(!checkIfContainsElement(liveOutSet, element) && checkIfContainsElement(liveInSet, element) && !liveOutSet.isEmpty()) {
                            continue;
                        }

                        addToInterferenceGraph(interferenceGraph, element, element2);
                    }
                }
            }

        }
        /*
        System.out.println("-------------------------");

        for (Map.Entry<Element, Set<Element>> entry : interferenceGraph.entrySet()) {
            System.out.println("Node: " + entry.getKey() + " -> Interfering Nodes: " + entry.getValue());
        }
        System.out.println("-------------------------");
        */
        int k = 4;
        return colorGraph(interferenceGraph, k, method);

    }

    private Map<Element, Integer> colorGraph(Map<Element, Set<Element>> interferenceGraph, int k, Method method) {
        Map<Element, Set<Element>> workGraph = new HashMap<>();
        for (Element element : interferenceGraph.keySet()) {
            workGraph.put(element, new HashSet<>(interferenceGraph.get(element)));
        }

        Stack<Element> stack = new Stack<>();
        Map<Element, Integer> colorMap = new HashMap<>();

        Set<Integer> reservedRegisters = new HashSet<>();

        if (!method.isStaticMethod()) {
            reservedRegisters.add(0);
        }

        int paramRegisters = method.getParams().size();
        int startReg = method.isStaticMethod() ? 0 : 1;
        for (int i = 0; i < paramRegisters; i++) {
            reservedRegisters.add(startReg + i);
        }

        while (!workGraph.isEmpty()) {
            boolean foundNode = false;

            for (Element node : workGraph.keySet()) {
                if (workGraph.get(node).size() < k) {
                    foundNode = true;

                    stack.push(node);

                    for (Element neighbor : interferenceGraph.get(node)) {
                        if (workGraph.containsKey(neighbor)) {
                            workGraph.get(neighbor).remove(node);
                        }
                    }
                    workGraph.remove(node);
                    break;
                }
            }

            if (!foundNode) {
                System.err.println("Cannot apply register allocation with " + k + " registers.");
                return null;
            }
        }

        while (!stack.isEmpty()) {
            Element node = stack.pop();

            Set<Integer> usedColors = new HashSet<>(reservedRegisters);

            for (Element neighbor : interferenceGraph.get(node)) {
                if (colorMap.containsKey(neighbor)) {
                    usedColors.add(colorMap.get(neighbor));
                }
            }

            int color = 0;
            while (usedColors.contains(color)) {
                color++;
            }

            colorMap.put(node, color);
        }

        /*
        System.out.println("COLOR MAP: ");
        for (Element var : colorMap.keySet()) {
            int register = colorMap.get(var);
            System.out.println("Variable " + var + " assigned to register " + register);
        }
        */
        return colorMap;
    }

}
