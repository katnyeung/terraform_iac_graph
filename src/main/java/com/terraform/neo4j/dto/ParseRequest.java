package com.terraform.neo4j.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for parsing Terraform content from text input.
 */
@Schema(description = "Request to parse Terraform configuration from text content")
public class ParseRequest {

    @Schema(description = "Terraform configuration content in HCL format", 
            example = "resource \"google_compute_instance\" \"vm\" {\n  name = \"test-vm\"\n  machine_type = \"e2-medium\"\n}")
    @NotBlank(message = "Terraform content cannot be blank")
    @Size(max = 1048576, message = "Terraform content cannot exceed 1MB")
    private String content;

    @Schema(description = "Optional description for this parsing operation", 
            example = "Production infrastructure configuration")
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    // Default constructor
    public ParseRequest() {}

    // Constructor with content
    public ParseRequest(String content) {
        this.content = content;
    }

    // Constructor with all fields
    public ParseRequest(String content, String description) {
        this.content = content;
        this.description = description;
    }

    // Getters and setters
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "ParseRequest{" +
                "contentLength=" + (content != null ? content.length() : 0) +
                ", description='" + description + '\'' +
                '}';
    }
}