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
            code.append(".field public '").append(field.getFieldName()).append("' ").append(types.getType(field.getFieldType())).append(NL);
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
                .filter(varInfo -> varInfo.getScope() == VarScope.LOCAL)
                .mapToInt(Descriptor::getVirtualReg)
                .max();

        return maxIndex.orElse(0);
    }

    private String generateMethod(Method method) {
        //System.out.println("STARTING METHOD " + method.getMethodName());
        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = types.getModifier(method.getMethodAccessModifier());

        var methodName = method.getMethodName();

        StringBuilder params = new StringBuilder();
        if(!method.getParams().isEmpty()) {
            for(var param : method.getParams()) {
                params.append(types.getType(param.getType()));
            }
        }

        var returnType = types.getType(method.getReturnType());

        code.append("\n.method ").append(modifier)
                .append(methodName).append("(").append(params).append(")").append(returnType).append(NL);

        // Add limits
        int maxVarIndex = getMaxVarIndex(method.getVarTable());
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals ").append(maxVarIndex + 1).append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;
        //System.out.println("ENDING METHOD " + method.getMethodName());
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());

        String operandStr = types.getType(operand.getType());
        if(operandStr.equals("V")) {
            operandStr = "";
        }
        if(operandStr.startsWith("[") || operand.getType() instanceof ArrayType || operand.getType() instanceof ClassType) {
            operandStr = "a";
        }
        code.append(operandStr.toLowerCase()).append("store_").append(reg.getVirtualReg()).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        int value = Integer.parseInt(literal.getLiteral());
        String inst;
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
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());
        String regStr = types.getType(operand.getType());
        if(regStr.equals("V")) {
            regStr = "";
        }
        if(regStr.startsWith("[") || operand.getType() instanceof ArrayType || operand.getType() instanceof ClassType) {
            regStr = "a";
        }

        return regStr.toLowerCase() + "load_" + reg.getVirtualReg() + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));
        // TODO: Hardcoded for int type, needs to be expanded
        var typePrefix = "i";

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "add";
            case MUL -> "mul";
            case DIV -> "div";
            case SUB -> "sub";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(typePrefix + op).append(NL);

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

        if(returnInst.getOperand().isPresent() && returnInst.getOperand().get() instanceof Operand) {
            code.append(generateOperand((Operand) returnInst.getOperand().get()));
        } else if(returnInst.getOperand().isPresent()) {
            code.append(apply(returnInst.getOperand().get()));
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
            code.append("astore_1").append(NL);
            code.append("aload_1").append(NL);
            code.append("invokenonvirtual ").append(((ClassType) newInst.getReturnType()).getName()).append("/<init>()V").append(NL);
            code.append("aload_1").append(NL);
        }


        return code.toString();
    }

    private String generateInvokeSpecial(InvokeSpecialInstruction invokeInst) {
        return "";
    }

    private String generatePutFieldInstruction(PutFieldInstruction putFieldInst) {
        StringBuilder code = new StringBuilder();
        int reg = currentMethod.getVarTable().get(putFieldInst.getObject().getName()).getVirtualReg();
        code.append("aload_").append(reg).append(NL);
        LiteralElement literalElement = (LiteralElement) putFieldInst.getValue();

        code.append("bipush ").append(literalElement.getLiteral()).append(NL);
        code.append("putfield ").append(currentMethod.getOllirClass().getClassName()).append("/").append(putFieldInst.getField().getName()).append(" ").append(types.getType(literalElement.getType())).append(NL);
        return code.toString();
    }

    private String generateGetFieldInstruction(GetFieldInstruction getFieldInst) {
        StringBuilder code = new StringBuilder();
        int reg = currentMethod.getVarTable().get(getFieldInst.getObject().getName()).getVirtualReg();
        code.append("aload_").append(reg).append(NL);
        code.append("getfield ").append(currentMethod.getOllirClass().getClassName()).append("/").append(getFieldInst.getField().getName()).append(" ").append(types.getType(getFieldInst.getField().getType())).append(NL);
        return code.toString();
    }

    private String generateOpCondInstruction(OpCondInstruction opCondInst) {
        return "";
    }

    private String generateGoToInstruction(GotoInstruction gotoInst) {
        return "";
    }

    private String generateInvokeStatic(InvokeStaticInstruction invokeInst) {
        return "";
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCondInst) {
        return "";
    }

    private String generateInvokeVirtual(InvokeVirtualInstruction invokeInst) {
        return "";
    }

    private String generateUnaryOpInstruction(UnaryOpInstruction unaryOpInst) {
        return "";
    }
}