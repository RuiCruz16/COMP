package pt.up.fe.comp2025.symboltable;

import org.antlr.v4.runtime.misc.Pair;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.*;
import java.util.stream.Collectors;

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

    private void checkDuplicates(List<Symbol> fields, List<String> methods, Map<String, List<Symbol>> locals, Map<String, List<Symbol>> params) {
        for (Symbol field : fields) {
            String name = field.getName();
            if (methods.contains(name)) {
                //reports.add(newError(, "Field and method names must be unique: '" + name + "'"));
            }

            for (Map.Entry<String, List<Symbol>> local : locals.entrySet()) {
                for (Map.Entry<String, List<Symbol>> param : params.entrySet()) {
                    List<Symbol> localsList = local.getValue();
                    List<Symbol> paramsList = param.getValue();
                    for (Symbol localSymbol : localsList) {
                        for (Symbol paramSymbol : paramsList) {
                            String localName = localSymbol.getName();
                            if (localName.equals(name)) {
                                //reports.add(newError(, "Field and local names must be unique: '" + name + "'"));
                            }

                            if (methods.contains(localName)) {
                                //reports.add(newError(, "Method and local names must be unique: '" + localName + "'"));
                            }

                            String paramName = paramSymbol.getName();
                            if (paramName.equals(name)) {
                                //reports.add(newError(, "Field and parameter names must be unique: '" + name + "'"));
                            }

                            if (methods.contains(paramName)) {
                                //reports.add(newError(, "Method and parameter names must be unique: '" + paramName + "'"));
                            }

                            if (local.getKey().equals(param.getKey())) {
                                if (localName.equals(paramName)) {
                                    //reports.add(newError(, "Local and parameter names from the same method must be unique: '" + localName + "'"));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public JmmSymbolTable build(JmmNode root) {
        System.out.println(root.toTree());
        var imports = buildImports(root);
        var classDecl = root.getChildren(CLASS_DECL).getFirst();

        String className = getClassName(classDecl);
        String superClassName = getSuperClassName(classDecl);

        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        //checkDuplicates(fields, methods, locals, params);

        var a = new JmmSymbolTable(className, methods, returnTypes, params, locals, imports, fields, superClassName);

        System.out.println(a);

        return a;
    }


    private List<String> buildImports(JmmNode node) {
        List<String> imports = new ArrayList<>();
        for(JmmNode importStmt : node.getChildren(IMPORT_DECL)) {
            String pkgName = importStmt.get("pck");
            if (imports.contains(pkgName)) {
                reports.add(newError(importStmt, "Duplicate import declaration"));
            }
            imports.add(pkgName);
        }
        return imports;
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            checkForVarArgs(method, "VarArgs can't be used as a method return type");
            var returnType = buildMethodType(method.getChild(0));
            map.put(name, returnType);
        }

        return map;
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
                if(map.containsKey(varName)) {
                    reports.add(newError(param, String.format("Duplicate parameter '%s'", varName)));
                }
                if(param.getChild(0).hasAttribute("suffix") && param.getChild(0).get("suffix").equals("...")) {
                    if(!params.getLast().equals(param)) {
                        reports.add(newError(param, String.format("VarArg type arguments such as variable '%s' must be the last one in a method call", varName)));
                    }
                }
                Type type = TypeUtils.convertType(param.getChild(0));
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
                checkForVarArgs(varDecl, "VarArgs can't be used as a local variable type");
                var type = buildMethodType(varDecl.getChild(0));
                var varName = varDecl.get("name");
                if(map.containsKey(varName)) {
                    reports.add(newError(varDecl, String.format("Duplicate local variable '%s'", varName)));
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
        var children = classDecl.getChildren();
        for (var child : children) {
            var kind = child.getKind();
            if(Objects.equals(kind, "ExtendsClause")) continue;
            if(!Objects.equals(kind, "VarDecl")) break;
            String name = child.get("name");
            checkForVarArgs(child, "VarArgs can't be used as a field type");
            var type = TypeUtils.convertType(child.getChild(0));
            Symbol symbol = new Symbol(type, name);
            if(fields.contains(symbol)) {
                reports.add(newError(classDecl, String.format("Duplicate field '%s'", name)));
            }
            fields.add(new Symbol(type, name));
        }
        return fields;
    }


    private void checkForVarArgs(JmmNode node, String message) {
        if(node.getChild(0).hasAttribute("suffix") && Objects.equals(node.getChild(0).get("suffix"), "...")) {
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
