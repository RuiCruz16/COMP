package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.Element;
import org.specs.comp.ollir.InstructionType;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Node;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.type.Type;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
                System.out.println("ENTERING METHOD NAMED -> " + method.getMethodName());
                initializeVariables(method);
                livenessAnalysis(method);
            }
        }

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

        System.out.println("CHECK DEFINED VARIABLES: ");
        for (Map.Entry<Instruction, Set<Element>> entry : definedVars.entrySet()) {
            System.out.println("Instruction: " + entry.getKey() + " -> Elements: " + entry.getValue());
        }
        System.out.println("-------------------------");


        System.out.println("CHECK USED VARIABLES: ");
        for (Map.Entry<Instruction, Set<Element>> entry : usedVars.entrySet()) {
            System.out.println("Instruction: " + entry.getKey() + " -> Elements: " + entry.getValue());
        }
        System.out.println("-------------------------");
    }

    private void livenessAnalysis(Method method) {

        System.out.println("-------------------------");
        System.out.println("LIVENESS ANALYSIS");

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

        System.out.println("CHECK LIVE IN VARIABLES: ");

        for (Map.Entry<Instruction, Set<Element>> entry : liveIn.entrySet()) {
            System.out.println("Instruction: " + entry.getKey() + " -> Elements: " + entry.getValue());
        }

        System.out.println("LIVE OUT VARIABLES: ");

        for (Map.Entry<Instruction, Set<Element>> entry : liveOut.entrySet()) {
            System.out.println("Instruction: " + entry.getKey() + " -> Elements: " + entry.getValue());
        }


        System.out.println("-------------------------");
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
            /*
                ASSIGN, // DONE
                CALL, // DONE
                GOTO, // ??
                BRANCH, // ??
                RETURN, // DONE
                PUTFIELD, // DONE
                GETFIELD, // DONE
                UNARYOPER, // DONE
                BINARYOPER, // DONE
                NOPER; // DONE
             */
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

        }
    }

    private void usedInReturns(ReturnInstruction instruction, AssignInstruction assignInstruction) {
        //System.out.println("RETURN HAS VALUE" + instruction.hasReturnValue());
        //System.out.println("1 Operand: " + instruction.getOperand());
        //System.out.println("1 Children: " + instruction.getChildren());
        //System.out.println("1 Inst Type: " + instruction.getInstType());

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
        //System.out.println("ASSIGN RHS: " + instruction.getRhs());

        trackUsedVariables(instruction.getRhs(), instruction);
    }

    private void usedInNopers(SingleOpInstruction instruction, AssignInstruction assignInstruction) {
        //System.out.println("NOPER IS LITERAL: " + instruction.getSingleOperand().isLiteral());

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
        //System.out.println("BinaryOpInstruction: " + instruction.getOperands() + " INSTRUCTION: " + instruction);

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
        //System.out.println("ENTERING UNARY: " + instruction.getOperand().isLiteral());

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
        //System.out.println("PUT FIELD: " + instruction.getField() + " INSTRUCTION VALUE: " + instruction.getValue() + " OPERANDS " + instruction.getOperands());
        //System.out.println("OBJECT: " + instruction.getObject());
        //System.out.println("VALUE IS LITERAL: " + instruction.getValue().isLiteral());
        //System.out.println("SUCCESSORS: " + instruction.getSuccessors());
        //System.out.println("DESCENDANTS: " + instruction.getDescendants());

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
        //System.out.println("GET FIELD: " + instruction.getField() + " INSTRUCTION OBJECT: " + instruction.getObject() + " OPERANDS " + instruction.getOperands());

        if (assignInstruction != null) {
            usedVars.computeIfAbsent(assignInstruction, k -> new HashSet<>()).add(instruction.getField());
        } else {
            usedVars.computeIfAbsent(instruction, k -> new HashSet<>()).add(instruction.getField());
        }
    }

    private void usedInCalls(CallInstruction instruction, AssignInstruction assignInstruction) {
        /*
        System.out.println("-------------------------------");
        System.out.println("INSTRUCTION -> " + instruction);
        System.out.println("CALL ARGUMENTS: " + instruction.getArguments());
        System.out.println("CALL OPERANDS: " + instruction.getOperands());
        System.out.println("CALL SUCCESSORS: " + instruction.getSuccessors());
        System.out.println("IS ISOLATED: " + instruction.isIsolated());
        System.out.println("METHOD NAME: " + instruction.getMethodName());
        System.out.println("CHILDREN:" + instruction.getChildren());
        System.out.println("CALLER: " + instruction.getCaller());
        System.out.println("-------------------------------");
        */

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
}
