package com.example.propertyview;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Property View application.
 */
@SpringBootApplication // Enables auto-configuration and component scanning.
@OpenAPIDefinition(info = @Info(
        title = "Property View API",
        version = "1.0.0",
        description = "Hotel catalog, search and histograms"
)) // Defines metadata for the generated OpenAPI documentation.
public class PropertyViewApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        // Initializes the application context and starts the embedded web server.
        SpringApplication.run(PropertyViewApplication.class, args);
    }
}