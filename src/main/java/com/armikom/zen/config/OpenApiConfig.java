package com.armikom.zen.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Zen Backend API")
                        .version("v1.0")
                        .description("Backend API for Zen application")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")));
    }
} 