package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }


    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    public String getType(Type type) {

        switch (type) {
            case BuiltinType builtinType -> {
                return getBuiltInType(builtinType);
            }
            case ArrayType arrayType -> {
                StringBuilder descriptor = new StringBuilder("[");
                Type elementType = arrayType.getElementType();

                if (elementType instanceof BuiltinType builtinElementType) {
                    descriptor.append(getBuiltInType(builtinElementType));
                    }
                return descriptor.toString();
            }
            case ClassType classType -> {
                return classType.getName();
            }
            default -> throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public String getBuiltInType(BuiltinType builtinType) {
        if (BuiltinType.is(builtinType, BuiltinKind.INT32)) {
            return "I";
        } else if (BuiltinType.is(builtinType, BuiltinKind.BOOLEAN)) {
            return "Z";
        } else if (BuiltinType.is(builtinType, BuiltinKind.STRING)) {
            return "Ljava/lang/String;";
        } else if (BuiltinType.is(builtinType, BuiltinKind.VOID)) {
            return "V";
        }
        return null;
    }
}
