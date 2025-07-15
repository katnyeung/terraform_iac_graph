package com.terraform.neo4j.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * Response DTO for Terraform parsing operations.
 */
@Schema(description = "Response containing the results of Terraform parsing operation")
public class ParseResponse {

    @Schema(description = "Whether the parsing operation was successful", example = "true")
    private boolean success;

    @Schema(description = "Human-readable message describing the result", 
            example = "Successfully parsed 5 resources from Terraform configuration")
    private String message;

    @Schema(description = "Number of resources successfully parsed", example = "5")
    private int resourceCount;

    @Schema(description = "Number of identity resources found", example = "2")
    private int identityResourceCount;

    @Schema(description = "Number of regular resources found", example = "3")
    private int regularResourceCount;

    @Schema(description = "List of cloud providers detected", example = "[\"GCP\", \"AWS\"]")
    private List<String> providersDetected;

    @Schema(description = "List of parsing errors, if any")
    private List<String> errors;

    @Schema(description = "Timestamp when the parsing was completed")
    private LocalDateTime timestamp;

    @Schema(description = "Summary of resource types found")
    private List<ResourceTypeSummary> resourceTypeSummary;

    // Default constructor
    public ParseResponse() {
        this.providersDetected = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.resourceTypeSummary = new ArrayList<>();
        this.timestamp = LocalDateTime.now();
    }

    // Constructor for successful response
    public ParseResponse(boolean success, String message, int resourceCount) {
        this();
        this.success = success;
        this.message = message;
        this.resourceCount = resourceCount;
    }

    // Static factory methods
    public static ParseResponse success(String message, int resourceCount) {
        return new ParseResponse(true, message, resourceCount);
    }

    public static ParseResponse failure(String message, List<String> errors) {
        ParseResponse response = new ParseResponse(false, message, 0);
        response.setErrors(errors != null ? new ArrayList<>(errors) : new ArrayList<>());
        return response;
    }

    public static ParseResponse failure(String message, String error) {
        ParseResponse response = new ParseResponse(false, message, 0);
        response.addError(error);
        return response;
    }

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this.resourceCount = resourceCount;
    }

    public int getIdentityResourceCount() {
        return identityResourceCount;
    }

    public void setIdentityResourceCount(int identityResourceCount) {
        this.identityResourceCount = identityResourceCount;
    }

    public int getRegularResourceCount() {
        return regularResourceCount;
    }

    public void setRegularResourceCount(int regularResourceCount) {
        this.regularResourceCount = regularResourceCount;
    }

    public List<String> getProvidersDetected() {
        return providersDetected;
    }

    public void setProvidersDetected(List<String> providersDetected) {
        this.providersDetected = providersDetected != null ? new ArrayList<>(providersDetected) : new ArrayList<>();
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<ResourceTypeSummary> getResourceTypeSummary() {
        return resourceTypeSummary;
    }

    public void setResourceTypeSummary(List<ResourceTypeSummary> resourceTypeSummary) {
        this.resourceTypeSummary = resourceTypeSummary != null ? new ArrayList<>(resourceTypeSummary) : new ArrayList<>();
    }

    // Utility methods
    public void addError(String error) {
        if (error != null && !error.trim().isEmpty()) {
            this.errors.add(error);
        }
    }

    public void addProvider(String provider) {
        if (provider != null && !provider.trim().isEmpty() && !this.providersDetected.contains(provider)) {
            this.providersDetected.add(provider);
        }
    }

    public void addResourceTypeSummary(String resourceType, int count) {
        this.resourceTypeSummary.add(new ResourceTypeSummary(resourceType, count));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        return "ParseResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", resourceCount=" + resourceCount +
                ", identityResourceCount=" + identityResourceCount +
                ", regularResourceCount=" + regularResourceCount +
                ", providersDetected=" + providersDetected +
                ", errorCount=" + errors.size() +
                ", timestamp=" + timestamp +
                '}';
    }

    /**
     * Inner class representing a summary of resource types found during parsing.
     */
    @Schema(description = "Summary of a specific resource type found during parsing")
    public static class ResourceTypeSummary {
        @Schema(description = "The Terraform resource type", example = "google_compute_instance")
        private String resourceType;

        @Schema(description = "Number of resources of this type found", example = "3")
        private int count;

        public ResourceTypeSummary() {}

        public ResourceTypeSummary(String resourceType, int count) {
            this.resourceType = resourceType;
            this.count = count;
        }

        public String getResourceType() {
            return resourceType;
        }

        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @Override
        public String toString() {
            return "ResourceTypeSummary{" +
                    "resourceType='" + resourceType + '\'' +
                    ", count=" + count +
                    '}';
        }
    }
}