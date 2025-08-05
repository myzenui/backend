package com.armikom.zen.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QuickTest {
    private PreviewService previewService;

    QuickTest(PreviewService previewService) {
        this.previewService = previewService;
    }

    @Test
    @DisplayName("Should compile project")
    void testCompile() {
        // Arrange
        String projectName = "test-project";
        String plantUml = Common.model;
        previewService.generatePreview(projectName, plantUml);
    }
}
