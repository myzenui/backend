package com.armikom.zen.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        sb.append("using System.Collections.Generic;\n");
        sb.append("using System.Collections.ObjectModel;\n\n");

        sb.append("namespace Zen.Model\n");
        sb.append("{\n\n");
        sb.append("    [DefaultClassOptions]\n");
        sb.append("    public class ").append(umlClass.name).append(" : BaseEntity\n");
        sb.append("    {\n");

        for (UmlAttribute attribute : umlClass.attributes) {
            sb.append("        public virtual ").append(mapType(attribute.type)).append(" ").append(capitalize(attribute.name)).append(" { get; set; }\n");
        }

        if (!umlClass.attributes.isEmpty() && !umlClass.relationships.isEmpty()) {
            sb.append("\n");
        }

        for (UmlRelationship relationship : umlClass.relationships) {
            String targetType = relationship.targetClass;
            String propertyName = relationship.propertyName;

            if (relationship.isCollection) {
                sb.append("        public virtual IList<").append(targetType).append("> ").append(propertyName)
                  .append(" { get; set; } = new ObservableCollection<").append(targetType).append(">();\n");
            } else {
                sb.append("        public virtual ").append(targetType).append("? ").append(propertyName).append(" { get; set; }\n");
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

        String[] lines = plantUml.split("\\r?\\n");
        Pattern labeledPattern = Pattern.compile("^\\s*(\\w+)\\s+\"([^\"]+)\"\\s+<-->\\s+\"([^\"]+)\"\\s+(\\w+)\\s*$");
        Pattern relPattern = Pattern.compile("^\\s*(\\w+)\\s+([*o]?--[*o]?)\\s+(\\w+).*");

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher labeledMatcher = labeledPattern.matcher(trimmed);
            if (labeledMatcher.matches()) {
                String class1Name = labeledMatcher.group(1);
                String leftLabel = labeledMatcher.group(2);
                String rightLabel = labeledMatcher.group(3);
                String class2Name = labeledMatcher.group(4);

                UmlClass class1 = classMap.get(class1Name);
                UmlClass class2 = classMap.get(class2Name);
                if (class1 == null || class2 == null) continue;

                boolean leftCollection = leftLabel.trim().startsWith("*");
                boolean rightCollection = rightLabel.trim().startsWith("*");
                String leftProp = leftLabel.trim().replaceFirst("^\\*\\s*", "");
                String rightProp = rightLabel.trim().replaceFirst("^\\*\\s*", "");

                class1.addRelationship(new UmlRelationship(rightProp, class2Name, rightCollection));
                class2.addRelationship(new UmlRelationship(leftProp, class1Name, leftCollection));
                continue;
            }

            Matcher relMatcher = relPattern.matcher(trimmed);
            if (relMatcher.matches()) {
                String class1Name = relMatcher.group(1);
                String operator = relMatcher.group(2);
                String class2Name = relMatcher.group(3);

                UmlClass class1 = classMap.get(class1Name);
                UmlClass class2 = classMap.get(class2Name);

                if (class1 == null || class2 == null) continue;

                boolean class1Collection;
                boolean class2Collection;

                if (operator.equals("o--") || operator.equals("*--")) {
                    class1Collection = true;
                    class2Collection = false;
                } else if (operator.equals("--o") || operator.equals("--*")) {
                    class1Collection = false;
                    class2Collection = true;
                } else { // "--"
                    class1Collection = true;
                    class2Collection = true;
                }

                String prop1 = class1Collection ? pluralize(class2Name) : class2Name;
                String prop2 = class2Collection ? pluralize(class1Name) : class1Name;

                class1.addRelationship(new UmlRelationship(prop1, class2Name, class1Collection));
                class2.addRelationship(new UmlRelationship(prop2, class1Name, class2Collection));
            }
        }
    }

    private String mapType(String plantUmlType) {
        switch (plantUmlType) {
            case "String":
                return "string?";
            case "Date":
                return "DateTime?";
            case "int":
                return "int?";
            case "float":
                return "float?";
            case "boolean":
                return "bool?";
            default:
                if (plantUmlType.startsWith("List<")) {
                    String genericType = plantUmlType.substring(5, plantUmlType.length() - 1);
                    return "IList<" + genericType + ">?";
                }
                return plantUmlType + "?";
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
        List<UmlClass> uniqueList = umlClasses.stream().distinct().toList();
        for (UmlClass umlClass : uniqueList) {
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

        // add compare to method to compare the name of the class
        // todo check namespace and class name if namespace feature is added
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            UmlClass umlClass = (UmlClass) obj;
            return name.equals(umlClass.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
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
        String propertyName;
        String targetClass;
        boolean isCollection;

        UmlRelationship(String propertyName, String targetClass, boolean isCollection) {
            this.propertyName = propertyName;
            this.targetClass = targetClass;
            this.isCollection = isCollection;
        }
    }
}
