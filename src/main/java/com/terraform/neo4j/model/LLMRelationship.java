package com.terraform.neo4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * Model representing a relationship between infrastructure resources identified by LLM.
 * This class encapsulates relationship information extracted from LLM analysis.
 */
public class LLMRelationship {
    private String source;
    private String target;
    private String type;
    private String description;
    private double confidence;

    public LLMRelationship() {
        this.confidence = 0.5; // Default confidence
    }

    public LLMRelationship(String source, String target, String type, String description, double confidence) {
        this.source = source;
        this.target = target;
        this.type = type;
        this.description = description;
        this.confidence = confidence;
    }

    /**
     * Creates LLMRelationship from a Map (typically from JSON parsing)
     */
    public static LLMRelationship fromMap(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Map cannot be null");
        }

        LLMRelationship relationship = new LLMRelationship();
        relationship.source = (String) map.get("source");
        relationship.target = (String) map.get("target");
        relationship.type = (String) map.get("type");
        relationship.description = (String) map.get("description");
        relationship.confidence = map.containsKey("confidence") ?
                ((Number) map.get("confidence")).doubleValue() : 0.5;

        // Validate required fields
        if (relationship.source == null || relationship.source.trim().isEmpty()) {
            throw new IllegalArgumentException("Relationship source cannot be null or empty");
        }
        if (relationship.target == null || relationship.target.trim().isEmpty()) {
            throw new IllegalArgumentException("Relationship target cannot be null or empty");
        }
        if (relationship.type == null || relationship.type.trim().isEmpty()) {
            throw new IllegalArgumentException("Relationship type cannot be null or empty");
        }

        // Validate confidence range
        if (relationship.confidence < 0.0 || relationship.confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }

        return relationship;
    }

    /**
     * Builder pattern for easy object construction
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String source;
        private String target;
        private String type;
        private String description;
        private double confidence = 0.5;

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder confidence(double confidence) {
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
            }
            this.confidence = confidence;
            return this;
        }

        public LLMRelationship build() {
            return new LLMRelationship(source, target, type, description, confidence);
        }
    }

    // Getters and setters
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        this.confidence = confidence;
    }

    /**
     * Validation method to check if relationship is valid
     */
    public boolean isValid() {
        return source != null && !source.trim().isEmpty() &&
               target != null && !target.trim().isEmpty() &&
               type != null && !type.trim().isEmpty() &&
               confidence >= 0.0 && confidence <= 1.0;
    }

    /**
     * Check if this is a high-confidence relationship (>= 0.8)
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    /**
     * Check if this is a bidirectional relationship type
     */
    public boolean isBidirectional() {
        return "CONNECTS_TO".equals(type) || 
               "COMMUNICATES_WITH".equals(type) ||
               "PEERS_WITH".equals(type);
    }

    /**
     * Get the reverse relationship type if applicable
     */
    public String getReverseType() {
        return switch (type) {
            case "DEPENDS_ON" -> "DEPENDED_ON_BY";
            case "PROVIDES_STORAGE_FOR" -> "USES_STORAGE_FROM";
            case "DEPLOYED_ON" -> "HOSTS";
            case "PROTECTED_BY" -> "PROTECTS";
            case "MANAGES" -> "MANAGED_BY";
            case "ROUTES_TO" -> "RECEIVES_TRAFFIC_FROM";
            case "MOUNTS" -> "MOUNTED_BY";
            case "CONFIGURES" -> "CONFIGURED_BY";
            case "USES" -> "USED_BY";
            default -> type; // For bidirectional types
        };
    }

    /**
     * Create a reverse relationship
     */
    public LLMRelationship createReverse() {
        return new LLMRelationship(target, source, getReverseType(), 
                                 "Reverse of: " + description, confidence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LLMRelationship that = (LLMRelationship) o;
        return Double.compare(that.confidence, confidence) == 0 &&
               Objects.equals(source, that.source) &&
               Objects.equals(target, that.target) &&
               Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, type, confidence);
    }

    @Override
    public String toString() {
        return String.format("LLMRelationship{source='%s', target='%s', type='%s', confidence=%.2f}",
                source, target, type, confidence);
    }
}