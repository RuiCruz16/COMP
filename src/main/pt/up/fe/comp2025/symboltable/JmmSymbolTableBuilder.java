package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private final List<Report> reports = new ArrayList<>();

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    private JmmNode stringToNode(JmmNode classDecl, String varName, String methodName) {
        JmmNode node = getFieldNode(classDecl, varName);
        if(node != null) return node;
        node = getLocalVarNode(classDecl, varName, methodName);
        if(node != null) return node;
        node = getParamNode(classDecl, varName, methodName);
        return node;
    }

    private JmmNode getFieldNode(JmmNode classDecl, String name) {
        for(JmmNode fieldDecl : classDecl.getChildren(VAR_DECL)) {
            if(fieldDecl.get("name").equals(name)) {
                return fieldDecl;
            }
        }
        return null;
    }

    private JmmNode getLocalVarNode(JmmNode classDecl, String varName, String methodName) {
        for(JmmNode methodDecl : classDecl.getChildren(METHOD_DECL)) {
            if(!methodDecl.get("name").equals(methodName)) continue;
            for (JmmNode paramDecl : methodDecl.getChildren(VAR_DECL)) {
                if(paramDecl.get("name").equals(varName)) {
                    return paramDecl;
                }
            }

        }
        return null;
    }

    private JmmNode getParamNode(JmmNode classDecl, String methodName, String paramName) {
        for(JmmNode methodDecl : classDecl.getChildren(METHOD_DECL)) {
            if(!methodDecl.get("name").equals(methodName)) continue;
            for (JmmNode paramDecl : methodDecl.getChildren(PARAM)) {
                if(paramDecl.get("name").equals(paramName)) {
                    return paramDecl;
                }
            }

        }
        return null;
    }

    private void checkDuplicates(Map<String, List<Symbol>> locals, Map<String, List<Symbol>> params, JmmNode classDecl) {
        for (Map.Entry<String, List<Symbol>> local : locals.entrySet()) {
            List<Symbol> localSymbols = local.getValue();
            String methodName = local.getKey();
            List<Symbol> paramSymbols = params.get(methodName);
            for (Symbol localSymbol : localSymbols) {
                for (Symbol paramSymbol : paramSymbols) {
                    if (paramSymbol.getName().equals(localSymbol.getName())) {
                        JmmNode localNode = stringToNode(classDecl, localSymbol.getName(), methodName);
                        if (localNode != null) {
                            reports.add(newError(localNode, "Method parameters and local variables cannot have the same name"));
                        }
                    }
                }
            }
        }
    }

    private boolean isCommonType(String type, String currentClass) {
        return (Objects.equals(type, "int") || Objects.equals(type, "String") || Objects.equals(type, "boolean") || Objects.equals(type, currentClass));
    }

    private boolean isTypeInImports(String typeName, List<String> imports) {
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

    private void checkInvalidTypes(List<Symbol> fields, Map<String, List<Symbol>> locals, Map<String, List<Symbol>> params, List<String> imports, JmmNode root, String className) {
        for (Symbol field : fields) {
            if (!isTypeInImports(field.getType().getName(), imports) && !isCommonType(field.getType().getName(), className)) {
                reports.add(newError(root, "Fields must have a valid type"));
            }
        }

        for (Map.Entry<String, List<Symbol>> param : params.entrySet()) {
            List<Symbol> paramSymbols = param.getValue();

            for (Symbol paramSymbol : paramSymbols) {
                if (!isTypeInImports(paramSymbol.getType().getName(), imports) && !isCommonType(paramSymbol.getType().getName(), className)) {
                    reports.add(newError(root, "Parameters must have a valid type"));
                }
            }
        }

        for (Map.Entry<String, List<Symbol>> local : locals.entrySet()) {
            List<Symbol> localSymbols = local.getValue();

            for (Symbol localSymbol : localSymbols) {
                if (!isTypeInImports(localSymbol.getType().getName(), imports) && !isCommonType(localSymbol.getType().getName(), className)) {
                    reports.add(newError(root, "Local variables must have a valid type"));
                }
            }
        }
    }


    public JmmSymbolTable build(JmmNode root) {
        var imports = buildImports(root);
        var classDecl = root.getChildren(CLASS_DECL).getFirst();
        String className = getClassName(classDecl);
        String superClassName = getSuperClassName(classDecl);
        if(superClassName != null && imports.stream().noneMatch(element -> element.contains(superClassName))) {
            reports.add(newError(classDecl, "Superclass not found. It must be imported."));
        }
        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        checkDuplicates(locals, params, classDecl);
        checkInvalidTypes(fields, locals, params, imports, root, className);

        return new JmmSymbolTable(className, methods, returnTypes, params, locals, imports, fields, superClassName);

    }

    private List<String> parsePackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return Collections.emptyList();
        }
        String cleaned = packageName.substring(1, packageName.length() - 1);
        return Arrays.asList(cleaned.split(", "));
    }


    private List<String> buildImports(JmmNode node) {
        List<String> imports = new ArrayList<>();
        Set<String> lastElements = new HashSet<>();

        for (JmmNode importStmt : node.getChildren(IMPORT_DECL)) {
            String pkgName = importStmt.get("pck");

            List<String> pkgSegments = parsePackageName(pkgName);

            String lastElement = pkgSegments.getLast();

            if (imports.contains(pkgName) || lastElements.contains(lastElement)) {
                reports.add(newError(importStmt, "Duplicate import declaration: " + pkgName));
            }

            imports.add(pkgName);
            lastElements.add(lastElement);
        }

        return imports;
    }


    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            checkForVarArgs(method.getChildren("Type").getFirst(), "VarArgs can't be used as a method return type");
            var returnType = buildMethodType(method.getChild(0));
            if(!name.equals("main") && returnType.getName().equals("void")) {
                reports.add(newError(classDecl, "Method return type can't be void"));
            }
            map.put(name, returnType);
        }

        return map;
    }


    private boolean containsVar(List<Symbol> vars, String name) {
        for(Symbol var : vars) {
            if(var.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            List<JmmNode> params = new ArrayList<>();
            List<Symbol> paramList = new ArrayList<>();
            try {
                params = method.getChild(1).getChild(0).getChildren(PARAM);
            }
            catch (Exception ignored) { } // method does not have parameters

            for (JmmNode param : params) {
                String varName = param.get("name");
                if(containsVar(paramList, varName)) {
                    reports.add(newError(param, String.format("Duplicate parameter '%s'", varName)));
                }
                if(!param.getChildren().getFirst().getChildren("SuffixPart").isEmpty() && !param.getChildren().getFirst().getChildren("SuffixPart").getFirst().getKind().equals("VarArgsSuffix")) {
                    if(!params.getLast().equals(param)) {
                        reports.add(newError(param, String.format("VarArg type arguments such as variable '%s' must be the last one in a method call", varName)));
                    }
                }
                Type type = TypeUtils.convertType(param.getChild(0));
                if(type.getName().equals("void")) {
                    reports.add(newError(param, "Void type can't be used as a parameter type"));
                }
                paramList.add(new Symbol(type, varName));
            }
            map.put(name, paramList);
        }

        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        var map = new HashMap<String, List<Symbol>>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            List<Symbol> locals = new ArrayList<>();
            for(var varDecl : method.getChildren(VAR_DECL)) {
                checkForVarArgs(varDecl.getChildren("Type").getFirst(), "VarArgs can't be used as a local variable type");
                var type = buildMethodType(varDecl.getChild(0));
                var varName = varDecl.get("name");
                for (Symbol localSymbol : locals) {
                    if (localSymbol.getName().equals(varName)) {
                        reports.add(newError(varDecl, String.format("Duplicate local variable '%s'", varName)));
                    }
                }
                if(type.getName().equals("void")) {
                    reports.add(newError(varDecl, "Void type can't be used as a local variable type"));
                }
                locals.add(new Symbol(type, varName));
            }
            map.put(name, locals);
        }

        return map;
    }

    private List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();

        List<String> aux = new ArrayList<>();

        for (String methodName : methods) {
            if(aux.contains(methodName)) {
                reports.add(newError(classDecl, String.format("Duplicate method '%s'", methodName)));
            }
            aux.add(methodName);
        }

        return methods;
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();
        var varDecls = classDecl.getChildren(VAR_DECL);
        for (var varDecl : varDecls) {
            if(varDecl.getParent().getKind().equals(CLASS_DECL.toString())) {
                checkForVarArgs(varDecl.getChildren("Type").getFirst(), "VarArgs can't be used as a field type");
                String name = varDecl.get("name");
                var type = TypeUtils.convertType(varDecl.getChild(0));
                Symbol symbol = new Symbol(type, name);
                if(type.getName().equals("void")) {
                    reports.add(newError(varDecl, "Void type can't be used as a field variable type"));
                }
                if(fields.contains(symbol)) {
                    reports.add(newError(classDecl, String.format("Duplicate field '%s'", name)));
                }
                fields.add(symbol);
            }
        }
        return fields;
    }


    private void checkForVarArgs(JmmNode node, String message) {
        if(node.getChildren().isEmpty()) return;
        if(!node.getChildren().getFirst().getChildren("VarArgsSuffix").isEmpty()) {
            reports.add(newError(node, message));
        }
    }

    private Type buildMethodType(JmmNode returnType) {
        return TypeUtils.convertType(returnType);
    }

    private String getClassName(JmmNode classDecl) {
        return classDecl.get("name");
    }

    private String getSuperClassName(JmmNode classDecl) {
        String superClassName = null;
        try {
            superClassName = classDecl.getChild(0).getChild(0).get("superclass");
        } catch (Exception ignored) {
        }
        return superClassName;
    }
}
