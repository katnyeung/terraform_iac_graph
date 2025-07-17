package com.terraform.neo4j.model;

import java.util.List;
import java.util.Map;

/**
 * Model representing Terraform content enhanced with contextual information
 */
public class ContextualizedContent {
    private String infrastructureOverview;
    private Map<String, String> fileContentsWithBoundaries;
    private Map<String, List<String>> crossReferenceComments;
    private Map<String, List<String>> relationshipHints;
    private LogicalGroups logicalGroups;
    private DependencyMap dependencies;
    private Map<String, Object> metadata;

    public ContextualizedContent() {}

    public ContextualizedContent(String infrastructureOverview,
                               Map<String, String> fileContentsWithBoundaries,
                               Map<String, List<String>> crossReferenceComments,
                               Map<String, List<String>> relationshipHints,
                               LogicalGroups logicalGroups,
                               DependencyMap dependencies) {
        this.infrastructureOverview = infrastructureOverview;
        this.fileContentsWithBoundaries = fileContentsWithBoundaries;
        this.crossReferenceComments = crossReferenceComments;
        this.relationshipHints = relationshipHints;
        this.logicalGroups = logicalGroups;
        this.dependencies = dependencies;
        this.metadata = new java.util.HashMap<>();
    }

    // Getters and setters
    public String getInfrastructureOverview() {
        return infrastructureOverview;
    }

    public void setInfrastructureOverview(String infrastructureOverview) {
        this.infrastructureOverview = infrastructureOverview;
    }

    public Map<String, String> getFileContentsWithBoundaries() {
        return fileContentsWithBoundaries;
    }

    public void setFileContentsWithBoundaries(Map<String, String> fileContentsWithBoundaries) {
        this.fileContentsWithBoundaries = fileContentsWithBoundaries;
    }

    public Map<String, List<String>> getCrossReferenceComments() {
        return crossReferenceComments;
    }

    public void setCrossReferenceComments(Map<String, List<String>> crossReferenceComments) {
        this.crossReferenceComments = crossReferenceComments;
    }

    public Map<String, List<String>> getRelationshipHints() {
        return relationshipHints;
    }

    public void setRelationshipHints(Map<String, List<String>> relationshipHints) {
        this.relationshipHints = relationshipHints;
    }

    public LogicalGroups getLogicalGroups() {
        return logicalGroups;
    }

    public void setLogicalGroups(LogicalGroups logicalGroups) {
        this.logicalGroups = logicalGroups;
    }

    public DependencyMap getDependencies() {
        return dependencies;
    }

    public void setDependencies(DependencyMap dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Add metadata entry
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get total number of files with content
     */
    public int getTotalFiles() {
        return fileContentsWithBoundaries != null ? fileContentsWithBoundaries.size() : 0;
    }

    /**
     * Get total number of cross-reference comments
     */
    public int getTotalCrossReferences() {
        return crossReferenceComments != null ? 
            crossReferenceComments.values().stream().mapToInt(List::size).sum() : 0;
    }

    /**
     * Get total number of relationship hints
     */
    public int getTotalRelationshipHints() {
        return relationshipHints != null ? 
            relationshipHints.values().stream().mapToInt(List::size).sum() : 0;
    }
}