package com.terraform.neo4j.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Model representing the analyzed structure of Terraform files
 */
public class FileStructure {
    private Map<String, List<String>> resourcesByFile;
    private Map<String, List<String>> variablesByFile;
    private Map<String, List<String>> outputsByFile;
    private Set<String> providers;
    private Map<String, String> resourceTypes;
    private int totalResources;
    private int totalVariables;
    private int totalOutputs;

    public FileStructure() {}

    public FileStructure(Map<String, List<String>> resourcesByFile,
                        Map<String, List<String>> variablesByFile,
                        Map<String, List<String>> outputsByFile,
                        Set<String> providers,
                        Map<String, String> resourceTypes) {
        this.resourcesByFile = resourcesByFile;
        this.variablesByFile = variablesByFile;
        this.outputsByFile = outputsByFile;
        this.providers = providers;
        this.resourceTypes = resourceTypes;
        this.totalResources = resourcesByFile.values().stream().mapToInt(List::size).sum();
        this.totalVariables = variablesByFile.values().stream().mapToInt(List::size).sum();
        this.totalOutputs = outputsByFile.values().stream().mapToInt(List::size).sum();
    }

    // Getters and setters
    public Map<String, List<String>> getResourcesByFile() {
        return resourcesByFile;
    }

    public void setResourcesByFile(Map<String, List<String>> resourcesByFile) {
        this.resourcesByFile = resourcesByFile;
        this.totalResources = resourcesByFile != null ? 
            resourcesByFile.values().stream().mapToInt(List::size).sum() : 0;
    }

    public Map<String, List<String>> getVariablesByFile() {
        return variablesByFile;
    }

    public void setVariablesByFile(Map<String, List<String>> variablesByFile) {
        this.variablesByFile = variablesByFile;
        this.totalVariables = variablesByFile != null ? 
            variablesByFile.values().stream().mapToInt(List::size).sum() : 0;
    }

    public Map<String, List<String>> getOutputsByFile() {
        return outputsByFile;
    }

    public void setOutputsByFile(Map<String, List<String>> outputsByFile) {
        this.outputsByFile = outputsByFile;
        this.totalOutputs = outputsByFile != null ? 
            outputsByFile.values().stream().mapToInt(List::size).sum() : 0;
    }

    public Set<String> getProviders() {
        return providers;
    }

    public void setProviders(Set<String> providers) {
        this.providers = providers;
    }

    public Map<String, String> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(Map<String, String> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

    public int getTotalResources() {
        return totalResources;
    }

    public int getTotalVariables() {
        return totalVariables;
    }

    public int getTotalOutputs() {
        return totalOutputs;
    }
}