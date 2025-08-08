package com.armikom.zen.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlantUmlToCSharpService {

    public Map<String, String> generate(String plantUml) {
        // print the input PlantUML for debugging
        System.out.println("Input PlantUML:\n" + plantUml);
        List<UmlClass> umlClasses = parseClasses(plantUml);
        parseRelationships(plantUml, umlClasses);

        Map<String, String> generatedClasses = new HashMap<>();
        for (UmlClass umlClass : umlClasses) {
            generatedClasses.put(umlClass.name + ".cs", generateCSharpClass(umlClass));
        }
        
        // Add DbContext with DbSets for all classes from PlantUML
        generatedClasses.put("ZenContext.cs", generateDbContext(umlClasses));

        return generatedClasses;
    }


    private String generateCSharpClass(UmlClass umlClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("using DevExpress.Persistent.Base;\n");
        sb.append("using System;\n");
        sb.append("using System.Collections.Generic;\n\n");
        sb.append("namespace Zen.Model\n");
        sb.append("{\n\n");
        sb.append("    [DefaultClassOptions]\n");
        sb.append("    public class ").append(umlClass.name).append(" : BaseEntity\n");
        sb.append("    {\n");

        boolean hasCollections = umlClass.relationships.stream().anyMatch(r -> r.isCollection);
        if (hasCollections) {
            sb.append("        public ").append(umlClass.name).append("() {\n");
            for (UmlRelationship relationship : umlClass.relationships) {
                if (relationship.isCollection) {
                    String collectionName = pluralize(relationship.targetClass);
                    sb.append("            ").append(collectionName).append(" = new HashSet<").append(relationship.targetClass).append(">();\n");
                }
            }
            sb.append("        }\n\n");
        }

        for (UmlAttribute attribute : umlClass.attributes) {
            sb.append("        public virtual ").append(mapType(attribute.type)).append(" ").append(capitalize(attribute.name)).append(" { get; set; }\n");
        }

        if (!umlClass.attributes.isEmpty() && !umlClass.relationships.isEmpty()) {
            sb.append("\n");
        }

        for (UmlRelationship relationship : umlClass.relationships) {
            String targetType = relationship.targetClass;
            String propertyName = relationship.targetClass;

            if (relationship.isCollection) {
                String collectionName = pluralize(propertyName);
                sb.append("        public virtual ICollection<").append(targetType).append("> ").append(collectionName).append(" { get; set; }\n");
            } else {
                sb.append("        public virtual ").append(targetType).append(" ").append(propertyName).append(" { get; set; }\n");
            }
        }

        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private List<UmlClass> parseClasses(String plantUml) {
        List<UmlClass> classes = new ArrayList<>();
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)\\s*\\{(.*?)\\}", Pattern.DOTALL);
        Matcher classMatcher = classPattern.matcher(plantUml);

        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String classBody = classMatcher.group(2);
            UmlClass umlClass = new UmlClass(className);

            Pattern attributePattern = Pattern.compile("[+#-]\\s*(\\w+)\\s*:\\s*(\\w+(?:<\\w+>)?(?:\\s*\\[\\])?)");
            Matcher attributeMatcher = attributePattern.matcher(classBody);
            while (attributeMatcher.find()) {
                String attrName = attributeMatcher.group(1);
                String attrType = attributeMatcher.group(2);
                umlClass.addAttribute(new UmlAttribute(attrName, attrType));
            }
            classes.add(umlClass);
        }
        return classes;
    }

    private void parseRelationships(String plantUml, List<UmlClass> classes) {
        Map<String, UmlClass> classMap = new HashMap<>();
        for (UmlClass c : classes) {
            classMap.put(c.name, c);
        }

        Set<String> processedPairs = new HashSet<>();
        String[] lines = plantUml.split("\\r?\\n");
        Pattern relPattern = Pattern.compile("^\\s*(\\w+)\\s+([*o]?--[*o]?)\\s+(\\w+).*");

        for (String line : lines) {
            Matcher relMatcher = relPattern.matcher(line.trim());
            if (relMatcher.matches()) {
                String class1Name = relMatcher.group(1);
                String operator = relMatcher.group(2);
                String class2Name = relMatcher.group(3);

                UmlClass class1 = classMap.get(class1Name);
                UmlClass class2 = classMap.get(class2Name);

                if (class1 == null || class2 == null) continue;

                if (operator.equals("o--") || operator.equals("*--")) {
                    class1.addRelationship(new UmlRelationship(class2Name, true));
                    class2.addRelationship(new UmlRelationship(class1Name, false));
                } else if (operator.equals("--o") || operator.equals("--*")) {
                    class1.addRelationship(new UmlRelationship(class2Name, false));
                    class2.addRelationship(new UmlRelationship(class1Name, true));
                } else { // "--"
                    class1.addRelationship(new UmlRelationship(class2Name, true));
                    class2.addRelationship(new UmlRelationship(class1Name, true));
                }
            }
        }
    }

    private String mapType(String plantUmlType) {
        switch (plantUmlType) {
            case "String":
                return "string";
            case "Date":
                return "DateTime";
            case "int":
                return "int";
            case "float":
                return "float";
            case "boolean":
                return "bool";
            default:
                if (plantUmlType.startsWith("List<")) {
                    String genericType = plantUmlType.substring(5, plantUmlType.length() - 1);
                    return "ICollection<" + genericType + ">";
                }
                return plantUmlType;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        if (s.equalsIgnoreCase("id")) return "Id";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String pluralize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        if (s.endsWith("y")) {
            return s.substring(0, s.length() - 1) + "ies";
        }
        if (s.endsWith("s")) {
            return s + "es";
        }
        return s + "s";
    }

    private String generateDbContext(List<UmlClass> umlClasses) {
        StringBuilder sb = new StringBuilder();
        sb.append("using Microsoft.EntityFrameworkCore;\n");
        sb.append("using System;\n");
        sb.append("using System.Collections.Generic;\n");
        sb.append("using System.Linq;\n");
        sb.append("using System.Text;\n");
        sb.append("using System.Threading.Tasks;\n\n");
        sb.append("namespace Zen.Model\n");
        sb.append("{\n");
        sb.append("    public class ZenContext : DbContext\n");
        sb.append("    {\n");
        sb.append("        public ZenContext(DbContextOptions options) : base(options)\n");
        sb.append("        {\n");
        sb.append("        }\n\n");
        
        // Add DbSets for all classes from PlantUML
        for (UmlClass umlClass : umlClasses) {
            sb.append("        public DbSet<").append(umlClass.name).append("> ")
              .append(pluralize(umlClass.name)).append(" { get; set; }\n");
        }
        
        sb.append("    }\n");
        sb.append("}\n");
        
        return sb.toString();
    }

    private static class UmlClass {
        String name;
        List<UmlAttribute> attributes = new ArrayList<>();
        List<UmlRelationship> relationships = new ArrayList<>();

        UmlClass(String name) {
            this.name = name;
        }

        void addAttribute(UmlAttribute attribute) {
            this.attributes.add(attribute);
        }

        void addRelationship(UmlRelationship relationship) {
            this.relationships.add(relationship);
        }
    }

    private static class UmlAttribute {
        String name;
        String type;

        UmlAttribute(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    private static class UmlRelationship {
        String targetClass;
        boolean isCollection;

        UmlRelationship(String targetClass, boolean isCollection) {
            this.targetClass = targetClass;
            this.isCollection = isCollection;
        }
    }
}
