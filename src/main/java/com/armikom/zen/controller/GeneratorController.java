package com.armikom.zen.controller;

import com.armikom.zen.service.PlantUmlToCSharpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/generator")
public class GeneratorController {

    private final PlantUmlToCSharpService plantUmlToCSharpService;

    public GeneratorController(PlantUmlToCSharpService plantUmlToCSharpService) {
        this.plantUmlToCSharpService = plantUmlToCSharpService;
    }

    @PostMapping("/plantuml-to-csharp")
    public ResponseEntity<Map<String, String>> generateCSharp(@RequestBody String plantUml) {
        if (plantUml == null || plantUml.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, String> generatedFiles = plantUmlToCSharpService.generate(plantUml);
        return ResponseEntity.ok(generatedFiles);
    }
}
