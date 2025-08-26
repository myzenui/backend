package com.armikom.zen.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlantUmlToCSharpServiceTest {

    private final PlantUmlToCSharpService service = new PlantUmlToCSharpService();

    @Test
    public void testGenerateCSharpFromNewAssociationSyntax() {
        String plantUml = "@startuml\n" +
                "class Employee {\n" +
                "  + Name: string\n" +
                "}\n" +
                "\n" +
                "class Department {\n" +
                "  + Name: string\n" +
                "}\n" +
                "\n" +
                "class Project {\n" +
                "  + Name: string\n" +
                "}\n" +
                "\n" +
                "Employee \"Vehicles\" *-- \"Employee\" Vehicle\n" +
                "Employee \"Projects\" *--* \"Employees\" Project\n" +
                "Employee \"Department\" --* \"Employees\" Department\n" +
                "class Vehicle {\n" +
                "  + Name: string\n" +
                "}\n" +
                "@enduml";

        Map<String, String> files = service.generate(plantUml);
        
        // Verify we have all expected files
        assertNotNull(files);
        assertEquals(5, files.size()); // 4 entity classes + 1 ZenContext.cs
        assertTrue(files.containsKey("Employee.cs"));
        assertTrue(files.containsKey("Department.cs"));
        assertTrue(files.containsKey("Project.cs"));
        assertTrue(files.containsKey("Vehicle.cs"));
        assertTrue(files.containsKey("ZenContext.cs"));

        // Test Department class structure and properties
        String departmentClass = files.get("Department.cs");
        assertNotNull(departmentClass);
        assertTrue(departmentClass.contains("using DevExpress.Persistent.Base;"));
        assertTrue(departmentClass.contains("using System;"));
        assertTrue(departmentClass.contains("using System.Collections.Generic;"));
        assertTrue(departmentClass.contains("using System.Collections.ObjectModel;"));
        assertTrue(departmentClass.contains("namespace Zen.Model"));
        assertTrue(departmentClass.contains("[DefaultClassOptions]"));
        assertTrue(departmentClass.contains("public class Department : BaseEntity"));
        assertTrue(departmentClass.contains("public virtual string? Name { get; set; }"));
        assertTrue(departmentClass.contains("public virtual IList<Employee> Employees { get; set; } = new ObservableCollection<Employee>();"));

        // Test Project class structure and properties
        String projectClass = files.get("Project.cs");
        assertNotNull(projectClass);
        assertTrue(projectClass.contains("using DevExpress.Persistent.Base;"));
        assertTrue(projectClass.contains("namespace Zen.Model"));
        assertTrue(projectClass.contains("[DefaultClassOptions]"));
        assertTrue(projectClass.contains("public class Project : BaseEntity"));
        assertTrue(projectClass.contains("public virtual string? Name { get; set; }"));
        assertTrue(projectClass.contains("public virtual IList<Employee> Employees { get; set; } = new ObservableCollection<Employee>();"));

        // Test Employee class structure and properties
        String employeeClass = files.get("Employee.cs");
        assertNotNull(employeeClass);
        assertTrue(employeeClass.contains("using DevExpress.Persistent.Base;"));
        assertTrue(employeeClass.contains("namespace Zen.Model"));
        assertTrue(employeeClass.contains("[DefaultClassOptions]"));
        assertTrue(employeeClass.contains("public class Employee : BaseEntity"));
        assertTrue(employeeClass.contains("public virtual string? Name { get; set; }"));
        assertTrue(employeeClass.contains("public virtual IList<Vehicle> Vehicles { get; set; } = new ObservableCollection<Vehicle>();"));
        assertTrue(employeeClass.contains("public virtual IList<Project> Projects { get; set; } = new ObservableCollection<Project>();"));
        assertTrue(employeeClass.contains("public virtual Department? Department { get; set; }"));

        // Test Vehicle class structure and properties
        String vehicleClass = files.get("Vehicle.cs");
        assertNotNull(vehicleClass);
        assertTrue(vehicleClass.contains("using DevExpress.Persistent.Base;"));
        assertTrue(vehicleClass.contains("namespace Zen.Model"));
        assertTrue(vehicleClass.contains("[DefaultClassOptions]"));
        assertTrue(vehicleClass.contains("public class Vehicle : BaseEntity"));
        assertTrue(vehicleClass.contains("public virtual string? Name { get; set; }"));
        assertTrue(vehicleClass.contains("public virtual Employee? Employee { get; set; }"));

        // Test ZenContext class structure and properties
        String contextClass = files.get("ZenContext.cs");
        assertNotNull(contextClass);
        assertTrue(contextClass.contains("using Microsoft.EntityFrameworkCore;"));
        assertTrue(contextClass.contains("namespace Zen.Model"));
        assertTrue(contextClass.contains("public class ZenContext : DbContext"));
        assertTrue(contextClass.contains("public ZenContext(DbContextOptions options) : base(options)"));
        assertTrue(contextClass.contains("public DbSet<Employee> Employees { get; set; }"));
        assertTrue(contextClass.contains("public DbSet<Department> Departments { get; set; }"));
        assertTrue(contextClass.contains("public DbSet<Project> Projects { get; set; }"));
        assertTrue(contextClass.contains("public DbSet<Vehicle> Vehicles { get; set; }"));
    }
}
