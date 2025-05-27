package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;
import org.specs.comp.ollir.type.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import static org.specs.comp.ollir.OperationType.GTH;
import static org.specs.comp.ollir.OperationType.LTH;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final JasminUtils types;
    private int currentStack = 0;
    private int maxStack = 0;
    private int labelCounter = 0;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        types = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(NewInstruction.class, this::generateNewInstruction);
        generators.put(InvokeSpecialInstruction.class, this::generateInvokeSpecial);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstruction);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstruction);
        generators.put(OpCondInstruction.class, this::generateOpCondInstruction);
        generators.put(GotoInstruction.class, this::generateGoToInstruction);
        generators.put(InvokeStaticInstruction.class, this::generateInvokeStatic);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(InvokeVirtualInstruction.class, this::generateInvokeVirtual);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOpInstruction);
        generators.put(ArrayLengthInstruction.class, this::generateArrayLengthInstruction);
    }

    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));

        return code.toString();
    }


    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        String superClass = ollirResult.getOllirClass().getSuperClass();
        var fullSuperClass = superClass != null ? superClass : "java/lang/Object";

        code.append(".super ").append(fullSuperClass).append(NL);

        for (var field : ollirResult.getOllirClass().getFields()) {
            code.append(".field public '").append(field.getFieldName()).append("' ");
            if (BuiltinType.is(field.getFieldType(), BuiltinKind.BOOLEAN)) {
                code.append("Z").append(NL);
            } else {
                code.append(types.getType(field.getFieldType())).append(NL);
            }
        }
        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }


    private int getMaxVarIndex(Map<String, Descriptor> varTable) {
        OptionalInt maxIndex = varTable.values().stream()
                .mapToInt(Descriptor::getVirtualReg)
                .max();

        return maxIndex.orElse(0);
    }

    private String generateMethod(Method method) {
        // set method
        currentMethod = method;
        currentStack = 0;
        maxStack = 0;
        labelCounter = 0;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = types.getModifier(method.getMethodAccessModifier());

        var methodName = method.getMethodName();

        if (methodName.equals("main")) {
            modifier += "static ";
        }

        StringBuilder params = new StringBuilder();
        if(!method.getParams().isEmpty()) {
            for(var param : method.getParams()) {
                if (BuiltinType.is(param.getType(), BuiltinKind.BOOLEAN)) {
                    params.append("Z");
                } else {
                    params.append(types.getType(param.getType()));
                }
            }
        }

        boolean isReturnBool = BuiltinType.is(method.getReturnType(), BuiltinKind.BOOLEAN);
        var returnType = isReturnBool ? "Z" : types.getType(method.getReturnType());

        code.append("\n.method ").append(modifier)
                .append(methodName).append("(").append(params).append(")").append(returnType).append(NL);

        // Add limits
        int maxVarIndex = getMaxVarIndex(method.getVarTable());

        for (var inst : method.getInstructions()) {
            for(var label : method.getLabels(inst)) {
                code.append(label).append(":").append(NL);
            }

            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(TAB).append(".limit stack ").append(maxStack).append(NL);
        code.append(TAB).append(".limit locals ").append(maxVarIndex + 1).append(NL);
        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private boolean checkIncrement(AssignInstruction assign) {
        if (assign.getRhs() instanceof BinaryOpInstruction binaryOp) {
            if (binaryOp.getOperation().getOpType().equals(OperationType.ADD)) {
                Operand left = (Operand) assign.getDest();
                boolean allOperandsAreEqual = true;
                boolean found = false;
                for(var operand: binaryOp.getOperands()) {
                    if (operand instanceof Operand) {
                        if (((Operand) operand).getName().equals(left.getName())) {
                            found = true;
                        }
                    }
                    else allOperandsAreEqual = false;
                }
                return found && !allOperandsAreEqual;
            }
        }

        // tmp variable
        if (assign.getRhs() instanceof SingleOpInstruction singleOp &&
                singleOp.getSingleOperand() instanceof Operand tempVar) {
            var instructions = currentMethod.getInstructions();
            for (var inst : instructions) {
                if (inst instanceof AssignInstruction prevAssign &&
                        prevAssign.getDest() instanceof Operand dest &&
                        dest.getName().equals(tempVar.getName()) &&
                        prevAssign.getRhs() instanceof BinaryOpInstruction binaryOp) {

                    if (binaryOp.getOperation().getOpType().equals(OperationType.ADD)) {
                        Operand target = (Operand) assign.getDest();
                        boolean hasTarget = false;
                        boolean hasLiteral = false;

                        for (var operand : binaryOp.getOperands()) {
                            if (operand instanceof Operand op &&
                                    op.getName().equals(target.getName())) {
                                hasTarget = true;
                            }
                            if (operand instanceof LiteralElement) {
                                hasLiteral = true;
                            }
                        }

                        return hasTarget && hasLiteral;
                    }
                }
            }
        }

        return false;
    }

    private String handleIncrement(AssignInstruction assign) {
        StringBuilder code = new StringBuilder();

        var operand = (Operand) assign.getDest();
        var reg = currentMethod.getVarTable().get(operand.getName());
        String literalStr = "";

        if (assign.getRhs() instanceof BinaryOpInstruction binaryOp) {
            for(var ops: binaryOp.getOperands()) {
                if(ops instanceof LiteralElement literal) {
                    literalStr = literal.getLiteral();
                }
            }
        } else if (assign.getRhs() instanceof SingleOpInstruction singleOp) {
            var tempVar = (Operand) singleOp.getSingleOperand();
            for (var inst : currentMethod.getInstructions()) {
                if (inst instanceof AssignInstruction prevAssign &&
                        prevAssign.getDest() instanceof Operand dest &&
                        dest.getName().equals(tempVar.getName()) &&
                        prevAssign.getRhs() instanceof BinaryOpInstruction binaryOp) {

                    for(var ops: binaryOp.getOperands()) {
                        if(ops instanceof LiteralElement literal) {
                            literalStr = literal.getLiteral();
                            break;
                        }
                    }
                    break;
                }
            }
        }

        code.append("iinc ").append(reg.getVirtualReg()).append(" ").append(literalStr).append(NL);
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // store value in the stack in destination
        var lhs = assign.getDest();


        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());

        if (checkIncrement(assign)) {
            code.append(handleIncrement(assign));
            return code.toString();
        }


        if (lhs instanceof ArrayOperand) {
            code.append(apply(assign.getDest()));
            code.append(apply(assign.getRhs()));
            code.append("iastore").append(NL);
            popStack();
            popStack();
            return code.toString();
        }

        // generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        if (assign.getRhs() instanceof SingleOpInstruction &&
                ((SingleOpInstruction) assign.getRhs()).getSingleOperand() instanceof ArrayOperand) {
            code.append("iaload").append(NL);
            pushStack();
        }

        String operandStr = types.getType(operand.getType());
        if(operandStr.equals("V")) {
            operandStr = "";
        }
        if(operandStr.startsWith("[") || operand.getType() instanceof ArrayType || operand.getType() instanceof ClassType) {
            operandStr = "a";
        }
        if(operandStr.equals("Ljava/lang/String;")) {
            operandStr = "a";
        }

        if (reg.getVirtualReg() >= 0 && reg.getVirtualReg() <= 3) {
            code.append(operandStr.toLowerCase()).append("store_").append(reg.getVirtualReg()).append(NL);
        } else {
            code.append(operandStr.toLowerCase()).append("store ").append(reg.getVirtualReg()).append(NL);
        }

        popStack();

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        int value = Integer.parseInt(literal.getLiteral());
        String inst;
        pushStack();
        if (value >= 0 && value <= 5) {
            inst = "iconst_";
        } else if (value >= -128 && value <= 127) {
            inst = "bipush ";
        } else if (value >= -32768 && value <= 32767) {
            inst = "sipush ";
        } else {
            inst = "ldc ";
        }
        return inst + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        var code = new StringBuilder();
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());
        String regStr = types.getType(operand.getType());

        if(regStr.equals("V")) {
            regStr = "";
        }
        if(regStr.startsWith("[") || operand.getType() instanceof ArrayType || operand instanceof ArrayOperand || operand.getType() instanceof ClassType) {
            regStr = "a";
        }

        if (reg.getVirtualReg() >= 0 && reg.getVirtualReg() <= 3) {
            code.append(regStr.toLowerCase()).append("load_").append(reg.getVirtualReg()).append(NL);
            pushStack();

        } else {
            code.append(regStr.toLowerCase()).append("load ").append(reg.getVirtualReg()).append(NL);
            pushStack();

        }

        for (var child: operand.getChildren()) {
            code.append(apply(child));
        }

        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB, LTH, GTH -> "isub";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        popStack();
        popStack();
        pushStack();

        if(binaryOp.getOperation().getOpType().equals(LTH) || binaryOp.getOperation().getOpType().equals(GTH)) {
            int currentLabel = labelCounter++;
            code.append(binaryOp.getOperation().getOpType().equals(LTH) ? "iflt" : "ifgt")
                    .append(" j_true_").append(currentLabel).append(NL);

            popStack();

            code.append("iconst_0").append(NL);
            pushStack();

            code.append("goto j_end_").append(currentLabel).append(NL);
            code.append("j_true_").append(currentLabel).append(":").append(NL);

            popStack();
            code.append("iconst_1").append(NL);
            pushStack();

            code.append("j_end_").append(currentLabel).append(":").append(NL);
        }

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        String returnStr = types.getType(returnInst.getReturnType());

        if(returnStr.equals("V")) {
            returnStr = "";
        }
        if(returnStr.startsWith("[") || returnInst.getReturnType() instanceof ArrayType || returnInst.getReturnType() instanceof ClassType) {
            returnStr = "a";
        }

        if(returnInst.getOperand().isPresent()) {
            code.append(apply(returnInst.getOperand().get()));
            popStack();
        }

        code.append(returnStr.toLowerCase()).append("return").append(NL);

        return code.toString();
    }

    private String generateNewInstruction(NewInstruction newInst) {
        var code = new StringBuilder();
        if (newInst.getOperands().size() > 1) {
            for (int i = 1; i < newInst.getOperands().size(); i++) {
                code.append(apply(newInst.getOperands().get(i)));
            }
        }

        if (newInst.getReturnType() instanceof ArrayType) {
            code.append("newarray int").append(NL);
        }
        else if(newInst.getReturnType() instanceof ClassType) {
            code.append("new ").append(((ClassType) newInst.getReturnType()).getName()).append(NL);
        }

        return code.toString();
    }

    private String generateInvokeSpecial(InvokeSpecialInstruction invokeInst) {
        var code = new StringBuilder();
        code.append(apply(invokeInst.getCaller()));

        String className = types.getClassName(invokeInst.getCaller().getType());
        String methodName = ((LiteralElement) invokeInst.getMethodName()).getLiteral();

        code.append("invokenonvirtual ").append(className).append("/").append(methodName).append("(");

        for (var arg: invokeInst.getArguments()) {
            code.append(types.getType(arg.getType()));
        }

        code.append(")V").append(NL);

        return code.toString();
    }

    private String generatePutFieldInstruction(PutFieldInstruction putFieldInst) {
        StringBuilder code = new StringBuilder();
        int reg = currentMethod.getVarTable().get(putFieldInst.getObject().getName()).getVirtualReg();
        code.append("aload_").append(reg).append(NL);
        pushStack();

        if (putFieldInst.getValue() instanceof LiteralElement) {
            LiteralElement literalElement = (LiteralElement) putFieldInst.getValue();
            code.append("bipush ").append(literalElement.getLiteral()).append(NL);
            code.append("putfield ").append(currentMethod.getOllirClass().getClassName()).append("/").append(putFieldInst.getField().getName()).append(" ").append(types.getType(literalElement.getType())).append(NL);
        } else {
            code.append(apply(putFieldInst.getValue())).append(NL);
            code.append("putfield ").append(currentMethod.getOllirClass().getClassName()).append("/").append(putFieldInst.getField().getName()).append(" ").append(types.getType(putFieldInst.getValue().getType())).append(NL);
        }

        popStack();
        popStack();

        return code.toString();
    }

    private String generateGetFieldInstruction(GetFieldInstruction getFieldInst) {
        StringBuilder code = new StringBuilder();
        int reg = currentMethod.getVarTable().get(getFieldInst.getObject().getName()).getVirtualReg();
        code.append("aload_").append(reg).append(NL);
        pushStack();
        code.append("getfield ").append(currentMethod.getOllirClass().getClassName()).append("/").append(getFieldInst.getField().getName()).append(" ").append(types.getType(getFieldInst.getField().getType())).append(NL);

        popStack();
        pushStack();
        return code.toString();
    }

    private String generateOpCondInstruction(OpCondInstruction opCondInst) {
        StringBuilder code = new StringBuilder();
        for(var operand: opCondInst.getCondition().getOperands()) {
            code.append(apply(operand));
        }

        // instead of using if 10 < 20, we use if 10 - 20 < 0
        code.append("isub").append(NL);
        String condition = switch (opCondInst.getCondition().getOperation().getOpType()) {
            case LTH -> "iflt";
            case GTE -> "ifge";
            case GTH -> "ifgt";
            case LTE -> "ifle";
            default -> throw new NotImplementedException(opCondInst.getCondition().getOperation().getOpType());
        };

        code.append(condition).append(" ").append(opCondInst.getLabel()).append(NL);

        return code.toString();
    }

    private String generateGoToInstruction(GotoInstruction gotoInst) {
        return "goto " + gotoInst.getLabel() + NL;
    }

    private String generateInvokeStatic(InvokeStaticInstruction invokeInst) {
        var code = new StringBuilder();
        for (var arg: invokeInst.getArguments()) {
            code.append(apply(arg)).append(NL);
        }

        String className = ((Operand) invokeInst.getCaller()).getName();
        String methodName = ((LiteralElement) invokeInst.getMethodName()).getLiteral();

        code.append("invokestatic ").append(className).append("/").append(methodName).append("(");

        for (var arg: invokeInst.getArguments()) {
            code.append(types.getType(arg.getType()));
        }

        for (var ignored: invokeInst.getArguments()) {
            popStack();
        }

        code.append(")").append(types.getType(invokeInst.getReturnType())).append(NL);

        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCondInst) {
        return apply(singleOpCondInst.getCondition()) +
                "ifne " + singleOpCondInst.getLabel() + NL;
    }

    private String generateInvokeVirtual(InvokeVirtualInstruction invokeInst) {
        var code = new StringBuilder();
        code.append(apply(invokeInst.getCaller()));
        for (var arg: invokeInst.getArguments()) {
            code.append(apply(arg)).append(NL);
        }
        String className = types.getClassName(invokeInst.getCaller().getType());
        String methodName = ((LiteralElement) invokeInst.getMethodName()).getLiteral();

        code.append("invokevirtual ").append(className).append("/").append(methodName).append("(");

        for (var arg: invokeInst.getArguments()) {
            String argType = types.getType(arg.getType());
            if(arg.getType() instanceof BuiltinType builtinType) {
                if (BuiltinType.is(builtinType, BuiltinKind.BOOLEAN)) {
                    argType = "Z";
                }
            }

            code.append(argType);
        }

        code.append(")").append(types.getType(invokeInst.getReturnType())).append(NL);

        popStack();

        for (int i = 0; i < invokeInst.getArguments().size(); i++) {
            popStack();
        }

        if (!BuiltinType.is(invokeInst.getReturnType(), BuiltinKind.VOID)) {
            boolean isValueUsed = isValueUsedInContext(invokeInst);

            if (isValueUsed) {
                pushStack();
            } else {
                code.append("pop").append(NL);
            }
        }

        return code.toString();
    }

    private boolean isValueUsedInContext(InvokeVirtualInstruction invokeInst) {
        for (Instruction inst : currentMethod.getInstructions()) {
            if (inst instanceof AssignInstruction assignInst) {
                if (assignInst.getRhs() == invokeInst) {
                    return true;
                }
            }
        }
        return false;
    }

    private String generateUnaryOpInstruction(UnaryOpInstruction unaryOpInst) {
        return apply(unaryOpInst.getOperand()) +
                "iconst_1" + NL +
                "ixor" + NL;
    }

    private String generateArrayLengthInstruction(ArrayLengthInstruction arrayLengthInstruction) {
        var code = new StringBuilder();

        code.append(apply(arrayLengthInstruction.getCaller()));

        code.append("arraylength").append(NL);

        return code.toString();
    }

    private void popStack() {
        updateMaxStack(-1);
    }

    private void pushStack() {
        updateMaxStack(1);
    }

    private void updateMaxStack(int value) {
        currentStack += value;
        if(currentStack > maxStack) {
            maxStack = currentStack;
        }
    }
}