package com.armikom.zen.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class StartupService {

    private final PlantUmlToCSharpService plantUmlToCSharpService;

    public StartupService(PlantUmlToCSharpService plantUmlToCSharpService) {
        this.plantUmlToCSharpService = plantUmlToCSharpService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicatinReady() {
        // This method will be called when the application is fully started
        // You can perform any startup logic here, such as initializing resources or logging
        System.out.println("Application is ready and running!");
//        plantUmlToCSharpService.generate("");

        // Example: You could initialize some services or load configurations here
        // For now, we just print a message to the console

    }
}
