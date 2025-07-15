package com.terraform.neo4j.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * Standard error response DTO for API error handling.
 */
@Schema(description = "Standard error response for API operations")
public class ErrorResponse {

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Error type or category", example = "VALIDATION_ERROR")
    private String error;

    @Schema(description = "Human-readable error message", 
            example = "Invalid Terraform configuration provided")
    private String message;

    @Schema(description = "Detailed error messages for debugging")
    private List<String> details;

    @Schema(description = "API path where the error occurred", example = "/api/terraform/parse")
    private String path;

    @Schema(description = "Timestamp when the error occurred")
    private LocalDateTime timestamp;

    // Default constructor
    public ErrorResponse() {
        this.details = new ArrayList<>();
        this.timestamp = LocalDateTime.now();
    }

    // Constructor with basic fields
    public ErrorResponse(int status, String error, String message) {
        this();
        this.status = status;
        this.error = error;
        this.message = message;
    }

    // Constructor with all fields
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message);
        this.path = path;
    }

    // Static factory methods
    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse(400, "BAD_REQUEST", message);
    }

    public static ErrorResponse badRequest(String message, List<String> details) {
        ErrorResponse response = badRequest(message);
        response.setDetails(details);
        return response;
    }

    public static ErrorResponse internalServerError(String message) {
        return new ErrorResponse(500, "INTERNAL_SERVER_ERROR", message);
    }

    public static ErrorResponse internalServerError(String message, String detail) {
        ErrorResponse response = internalServerError(message);
        response.addDetail(detail);
        return response;
    }

    public static ErrorResponse validationError(String message, List<String> details) {
        ErrorResponse response = new ErrorResponse(400, "VALIDATION_ERROR", message);
        response.setDetails(details);
        return response;
    }

    public static ErrorResponse parsingError(String message, List<String> details) {
        ErrorResponse response = new ErrorResponse(422, "PARSING_ERROR", message);
        response.setDetails(details);
        return response;
    }

    // Getters and setters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details != null ? new ArrayList<>(details) : new ArrayList<>();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // Utility methods
    public void addDetail(String detail) {
        if (detail != null && !detail.trim().isEmpty()) {
            this.details.add(detail);
        }
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "status=" + status +
                ", error='" + error + '\'' +
                ", message='" + message + '\'' +
                ", detailCount=" + details.size() +
                ", path='" + path + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}