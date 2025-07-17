package com.terraform.neo4j.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Model representing the final merged and formatted Terraform text optimized for LLM analysis
 */
public class MergedTerraformText {
    private String mergedContent;
    private List<String> fileNames;
    private Map<String, String> fileBoundaries;
    private DependencyMap crossReferences;
    private LogicalGroups resourceGroups;
    private int totalResources;
    private Set<String> providers;
    private Map<String, Object> metadata;
    private String formattingVersion;
    private long contentLength;

    public MergedTerraformText() {}

    public MergedTerraformText(String mergedContent,
                              List<String> fileNames,
                              Map<String, String> fileBoundaries,
                              DependencyMap crossReferences,
                              LogicalGroups resourceGroups,
                              int totalResources,
                              Set<String> providers) {
        this.mergedContent = mergedContent;
        this.fileNames = fileNames;
        this.fileBoundaries = fileBoundaries;
        this.crossReferences = crossReferences;
        this.resourceGroups = resourceGroups;
        this.totalResources = totalResources;
        this.providers = providers;
        this.contentLength = mergedContent != null ? mergedContent.length() : 0;
        this.formattingVersion = "1.0";
        this.metadata = new java.util.HashMap<>();
    }

    // Builder pattern for easy construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String mergedContent;
        private List<String> fileNames;
        private Map<String, String> fileBoundaries;
        private DependencyMap crossReferences;
        private LogicalGroups resourceGroups;
        private int totalResources;
        private Set<String> providers;
        private Map<String, Object> metadata = new java.util.HashMap<>();

        public Builder mergedContent(String mergedContent) {
            this.mergedContent = mergedContent;
            return this;
        }

        public Builder fileNames(List<String> fileNames) {
            this.fileNames = fileNames;
            return this;
        }

        public Builder fileBoundaries(Map<String, String> fileBoundaries) {
            this.fileBoundaries = fileBoundaries;
            return this;
        }

        public Builder crossReferences(DependencyMap crossReferences) {
            this.crossReferences = crossReferences;
            return this;
        }

        public Builder resourceGroups(LogicalGroups resourceGroups) {
            this.resourceGroups = resourceGroups;
            return this;
        }

        public Builder totalResources(int totalResources) {
            this.totalResources = totalResources;
            return this;
        }

        public Builder providers(Set<String> providers) {
            this.providers = providers;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public MergedTerraformText build() {
            MergedTerraformText result = new MergedTerraformText(
                mergedContent, fileNames, fileBoundaries, crossReferences,
                resourceGroups, totalResources, providers
            );
            result.setMetadata(metadata);
            return result;
        }
    }

    // Getters and setters
    public String getMergedContent() {
        return mergedContent;
    }

    public void setMergedContent(String mergedContent) {
        this.mergedContent = mergedContent;
        this.contentLength = mergedContent != null ? mergedContent.length() : 0;
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public void setFileNames(List<String> fileNames) {
        this.fileNames = fileNames;
    }

    public Map<String, String> getFileBoundaries() {
        return fileBoundaries;
    }

    public void setFileBoundaries(Map<String, String> fileBoundaries) {
        this.fileBoundaries = fileBoundaries;
    }

    public DependencyMap getCrossReferences() {
        return crossReferences;
    }

    public void setCrossReferences(DependencyMap crossReferences) {
        this.crossReferences = crossReferences;
    }

    public LogicalGroups getResourceGroups() {
        return resourceGroups;
    }

    public void setResourceGroups(LogicalGroups resourceGroups) {
        this.resourceGroups = resourceGroups;
    }

    public int getTotalResources() {
        return totalResources;
    }

    public void setTotalResources(int totalResources) {
        this.totalResources = totalResources;
    }

    public Set<String> getProviders() {
        return providers;
    }

    public void setProviders(Set<String> providers) {
        this.providers = providers;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getFormattingVersion() {
        return formattingVersion;
    }

    public void setFormattingVersion(String formattingVersion) {
        this.formattingVersion = formattingVersion;
    }

    public long getContentLength() {
        return contentLength;
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
     * Get metadata value
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * Check if content is ready for LLM processing
     */
    public boolean isReadyForLLM() {
        return mergedContent != null && 
               !mergedContent.trim().isEmpty() && 
               totalResources > 0 && 
               fileNames != null && 
               !fileNames.isEmpty();
    }

    /**
     * Get summary statistics
     */
    public String getSummary() {
        return String.format(
            "MergedTerraformText[files=%d, resources=%d, providers=%d, length=%d chars]",
            fileNames != null ? fileNames.size() : 0,
            totalResources,
            providers != null ? providers.size() : 0,
            contentLength
        );
    }

    /**
     * Get content preview (first 500 characters)
     */
    public String getContentPreview() {
        if (mergedContent == null || mergedContent.isEmpty()) {
            return "[No content]";
        }
        
        if (mergedContent.length() <= 500) {
            return mergedContent;
        }
        
        return mergedContent.substring(0, 500) + "... [truncated]";
    }
}