package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
    }

    public void setTypesCurrentMethod(String name) {
        this.types.setCurrentMethod(name);
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit("ArrayNew", this::visitNewArray);
        addVisit("TypeID", this::visitTypeId);
        addVisit("If", this::visitIfStmt);
        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitTypeId(JmmNode node, Void unused) {
        String type = ollirTypes.toOllirType(new Type(node.get("name"), false));
        return new OllirExprResult(type);
    }

    private OllirExprResult visitIfStmt(JmmNode node, Void unused) {
        return new OllirExprResult("if");
    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        JmmNode arrayNew = node.getChildren().getFirst();
        StringBuilder code = new StringBuilder();

        OllirExprResult typeId = null;
        for(JmmNode child : arrayNew.getChildren()) {
            if(child.getKind().equals("NewArray")) continue;

            if(child.getKind().equals("TypeID"))  {
                typeId = visit(child, unused);
                code.append("new(array,");
            }
            else if(types.getExprType(child) != null) {
                String val = child.get("value");
                String childTypeId = ollirTypes.toOllirType(types.getExprType(child));
                code.append(val).append(childTypeId);
            }

        }
        code.append(").array");
        if(typeId != null) {
            code.append(typeId.getCode());
        }
        StringBuilder computation = new StringBuilder();

        System.out.println("CODE: " + code.toString());
        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = types.getExprType(node);
        computation.append(node.get("op")).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("name");
        Type type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
