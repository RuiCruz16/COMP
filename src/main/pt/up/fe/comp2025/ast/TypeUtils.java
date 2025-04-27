package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;
import java.util.Objects;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;
    private String currentMethod;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public void setCurrentMethod(String currentMethod) {
        this.currentMethod = currentMethod;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type convertType(JmmNode typeNode) {
        var name = typeNode.get("name");

        var isArray = false;
        try{
            if(!typeNode.getChildren("SuffixPart").isEmpty()) {
                JmmNode suffixPart = typeNode.getChildren("SuffixPart").getFirst();
                if(!suffixPart.getChildren("ArraySuffix").isEmpty() || !suffixPart.getChildren("VarArgsSuffix").isEmpty()) {
                    isArray = true;
                }
            }
        } catch (Exception ignored){ }

        return new Type(name, isArray);
    }

    public Type getVarType(String varName) {

      for(Symbol s: table.getLocalVariables(currentMethod)) {
            if (s.getName().equals(varName)) {
                return s.getType();
            }
        }

        for(Symbol s: table.getParameters(currentMethod)) {
            if (s.getName().equals(varName)) {
                return s.getType();
            }
        }

        for(Symbol s: table.getFields()) {
            if (s.getName().equals(varName)) {
                return s.getType();
            }
        }

        if (varName.equals("this")) {
            return new Type(table.getClassName(), false);
        }

        return null;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr) {
        if (expr.getKind().equals(Kind.VAR_REF_EXPR.toString()) || expr.getKind().equals("Var")) {
            return getVarType(expr.get("name"));
        }
        else if (expr.getKind().equals("IntegerLiteral") || expr.getKind().equals("IntLit")) {
            return TypeUtils.newIntType();
        }
        else if (expr.getKind().equals("BooleanLiteral") || expr.getKind().equals("BooleanLit")) {
            return new Type("boolean", false);
        }
        else if (expr.getKind().equals("StringLiteral") || expr.getKind().equals("StringLit")) {
            return new Type("String", false);
        }
        else if (expr.getKind().equals("NewArray")) {
            return new Type("Array", true);
        }
        else if (expr.getKind().equals("ObjectNew")) {
            return new Type(expr.getChildren("NewObject").getFirst().get("name"), false);
        }
        else if(expr.getKind().equals("ArrayLit")) {
            Type arrayType = getExprType(expr.getChild(0));
            return new Type(arrayType.getName(), true);
        }
        else if (expr.getKind().equals("ArrayInit")) {
            return getExprType(expr.getChild(0));
        }
        else if (expr.getKind().equals("ObjectAccess")) {
            return new Type("ObjectAccess", true);
        }
        else if (expr.getKind().equals("This")) {
            return new Type(table.getClassName(), false);
        }
        else if (expr.getKind().equals("CallMethod")) {
            String methodName = expr.getChildren("MethodCall").getFirst().get("name");
            String objectType = getExprType(expr.getChildren("ObjectNew").getFirst()).getName();
            if(!objectType.equals(table.getClassName())) return null;
            return table.getReturnType(methodName);
        }
        else if(expr.getKind().equals("ObjectMethod")) {
            String methodName = expr.get("suffix");
            if(table.getMethods().contains(methodName)) {
                return  table.getReturnType(methodName);
            }
            return null;
        }
        else if (expr.getKind().equals("ObjectAttribute")) {
            String methodName = expr.get("suffix");
            if(methodName.equals("length")) {
                return new Type("int", false);
            }
            if(expr.get("var").equals("this")) {
                return getVarType(expr.get("suffix"));
            }
        }
        else if (expr.getKind().equals(Kind.ARRAY_ACCESS.toString())) {
            if(expr.getChild(0).getKind().equals("ArrayInit")) {
                return new Type("int", false);
            }
            String varName = expr.getChildren(Kind.VAR_REF_EXPR.toString()).getFirst().get("name");
            String literalTypeName = getVarType(varName).getName();
            return new Type(literalTypeName, false);
        }
        else if (expr.getKind().equals("NegExpr")) {
            Type innerType = getExprType(expr.getChildren().getFirst());
            if (innerType != null && innerType.getName().equals("boolean") && !innerType.isArray()) {
                return new Type("boolean", false);
            }
        }
        else if(expr.getKind().equals("LiteralAttribute")) {
            if(expr.get("suffix").equals("length")) {
                return new Type("int", false);
            }
            return null;
        }
        else if (expr.getKind().equals(Kind.BINARY_EXPR.toString())) {
            String opName = expr.get("op");
            if(
                    opName.equals("<") ||
                            opName.equals(">") ||
                            opName.equals("<=") ||
                            opName.equals(">=") ||
                            opName.equals("||") ||
                            opName.equals("&&"))
            {
                return new Type("boolean", false);
            }
            return TypeUtils.newIntType();
        }
        return getExprType(expr.getChildren().getFirst());
    }

    public boolean isTypeInImports(String typeName, List<String> imports) {
        for(String importName : imports) {
            String actualImportName = importName.replace("[", "")
                    .replace("]", "")
                    .replace(", ", ".");
            if(actualImportName.contains(typeName)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCommonType(String type) {
        return (Objects.equals(type, "int") || Objects.equals(type, "String") || Objects.equals(type, "boolean"));
    }

}