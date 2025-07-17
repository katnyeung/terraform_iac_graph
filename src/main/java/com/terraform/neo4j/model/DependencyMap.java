package com.terraform.neo4j.model;

import java.util.Map;
import java.util.Set;

/**
 * Model representing dependency relationships between Terraform resources
 */
public class DependencyMap {
    private Map<String, Set<String>> resourceDependencies;
    private Map<String, Set<String>> variableUsage;
    private Map<String, Set<String>> outputReferences;
    private Map<String, Set<String>> moduleReferences;
    private Map<String, Set<String>> implicitDependencies;
    private int totalDependencies;

    public DependencyMap() {}

    public DependencyMap(Map<String, Set<String>> resourceDependencies,
                        Map<String, Set<String>> variableUsage,
                        Map<String, Set<String>> outputReferences,
                        Map<String, Set<String>> moduleReferences,
                        Map<String, Set<String>> implicitDependencies) {
        this.resourceDependencies = resourceDependencies;
        this.variableUsage = variableUsage;
        this.outputReferences = outputReferences;
        this.moduleReferences = moduleReferences;
        this.implicitDependencies = implicitDependencies;
        this.totalDependencies = calculateTotalDependencies();
    }

    private int calculateTotalDependencies() {
        int total = 0;
        if (resourceDependencies != null) {
            total += resourceDependencies.values().stream().mapToInt(Set::size).sum();
        }
        if (variableUsage != null) {
            total += variableUsage.values().stream().mapToInt(Set::size).sum();
        }
        if (outputReferences != null) {
            total += outputReferences.values().stream().mapToInt(Set::size).sum();
        }
        if (moduleReferences != null) {
            total += moduleReferences.values().stream().mapToInt(Set::size).sum();
        }
        if (implicitDependencies != null) {
            total += implicitDependencies.values().stream().mapToInt(Set::size).sum();
        }
        return total;
    }

    // Getters and setters
    public Map<String, Set<String>> getResourceDependencies() {
        return resourceDependencies;
    }

    public void setResourceDependencies(Map<String, Set<String>> resourceDependencies) {
        this.resourceDependencies = resourceDependencies;
        this.totalDependencies = calculateTotalDependencies();
    }

    public Map<String, Set<String>> getVariableUsage() {
        return variableUsage;
    }

    public void setVariableUsage(Map<String, Set<String>> variableUsage) {
        this.variableUsage = variableUsage;
        this.totalDependencies = calculateTotalDependencies();
    }

    public Map<String, Set<String>> getOutputReferences() {
        return outputReferences;
    }

    public void setOutputReferences(Map<String, Set<String>> outputReferences) {
        this.outputReferences = outputReferences;
        this.totalDependencies = calculateTotalDependencies();
    }

    public Map<String, Set<String>> getModuleReferences() {
        return moduleReferences;
    }

    public void setModuleReferences(Map<String, Set<String>> moduleReferences) {
        this.moduleReferences = moduleReferences;
        this.totalDependencies = calculateTotalDependencies();
    }

    public Map<String, Set<String>> getImplicitDependencies() {
        return implicitDependencies;
    }

    public void setImplicitDependencies(Map<String, Set<String>> implicitDependencies) {
        this.implicitDependencies = implicitDependencies;
        this.totalDependencies = calculateTotalDependencies();
    }

    public int getTotalDependencies() {
        return totalDependencies;
    }

    /**
     * Get all dependencies for a specific resource
     */
    public Set<String> getAllDependenciesFor(String resource) {
        Set<String> allDeps = new java.util.HashSet<>();
        
        if (resourceDependencies != null && resourceDependencies.containsKey(resource)) {
            allDeps.addAll(resourceDependencies.get(resource));
        }
        if (variableUsage != null && variableUsage.containsKey(resource)) {
            allDeps.addAll(variableUsage.get(resource));
        }
        if (outputReferences != null && outputReferences.containsKey(resource)) {
            allDeps.addAll(outputReferences.get(resource));
        }
        if (moduleReferences != null && moduleReferences.containsKey(resource)) {
            allDeps.addAll(moduleReferences.get(resource));
        }
        if (implicitDependencies != null && implicitDependencies.containsKey(resource)) {
            allDeps.addAll(implicitDependencies.get(resource));
        }
        
        return allDeps;
    }
}