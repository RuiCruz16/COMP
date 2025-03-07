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

    public JmmSymbolTable build(JmmNode root) {

        var imports = buildImports(root);
        var classDecl = root.getChildren(CLASS_DECL).getFirst();

        String className = getClassName(classDecl);
        String superClassName = getSuperClassName(classDecl);

        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        var a = new JmmSymbolTable(className, methods, returnTypes, params, locals, imports, fields, superClassName);

        System.out.println(a);

        return a;
    }

    private List<String> buildImports(JmmNode node) {
        return node.getChildren(IMPORT_DECL).stream()
                .map(method -> method.get("pck"))
                .toList();
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");

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
                var type = buildMethodType(varDecl.getChild(0));
                var varName = varDecl.get("name");
                locals.add(new Symbol(type, varName));
            }
            map.put(name, locals);
        }

        return map;
    }

    private List<String> buildMethods(JmmNode classDecl) {
        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();
        var children = classDecl.getChildren();
        for (var child : children) {
            var kind = child.getKind();
            if(!Objects.equals(kind, "VarDecl")) break;
            String name = child.get("name");
            var type = TypeUtils.convertType(child.getChild(0));
            fields.add(new Symbol(type, name));
        }

        return fields;
    }

    private Type buildMethodType(JmmNode returnType) {
        return TypeUtils.convertType(returnType);
    }

    private String getClassName(JmmNode classDecl) {
        return classDecl.get("name");
    }

    private String getSuperClassName(JmmNode classDecl) {
        String superClassName = "";
        try {
            superClassName = classDecl.getChild(0).getChild(0).get("superclass");
        } catch (Exception e) {
            superClassName = "";
        }
        return superClassName;
    }
}
