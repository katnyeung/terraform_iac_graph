package com.terraform.neo4j.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Data model representing the complete parsed result of Terraform configuration files.
 * Contains all extracted resources, variables, outputs, and other Terraform constructs.
 */
public class ParsedTerraform {
    private List<TerraformResource> resources;
    private Map<String, Object> variables;
    private Map<String, Object> outputs;
    private Map<String, Object> providers;
    private List<String> parseErrors;

    // Default constructor
    public ParsedTerraform() {
        this.resources = new ArrayList<>();
        this.variables = new HashMap<>();
        this.outputs = new HashMap<>();
        this.providers = new HashMap<>();
        this.parseErrors = new ArrayList<>();
    }

    // Constructor with resources
    public ParsedTerraform(List<TerraformResource> resources) {
        this();
        this.resources = resources != null ? new ArrayList<>(resources) : new ArrayList<>();
    }

    // Full constructor
    public ParsedTerraform(List<TerraformResource> resources, 
                          Map<String, Object> variables,
                          Map<String, Object> outputs,
                          Map<String, Object> providers,
                          List<String> parseErrors) {
        this.resources = resources != null ? new ArrayList<>(resources) : new ArrayList<>();
        this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
        this.outputs = outputs != null ? new HashMap<>(outputs) : new HashMap<>();
        this.providers = providers != null ? new HashMap<>(providers) : new HashMap<>();
        this.parseErrors = parseErrors != null ? new ArrayList<>(parseErrors) : new ArrayList<>();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<TerraformResource> resources = new ArrayList<>();
        private Map<String, Object> variables = new HashMap<>();
        private Map<String, Object> outputs = new HashMap<>();
        private Map<String, Object> providers = new HashMap<>();
        private List<String> parseErrors = new ArrayList<>();

        public Builder resources(List<TerraformResource> resources) {
            this.resources = resources != null ? new ArrayList<>(resources) : new ArrayList<>();
            return this;
        }

        public Builder addResource(TerraformResource resource) {
            if (resource != null) {
                this.resources.add(resource);
            }
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
            return this;
        }

        public Builder outputs(Map<String, Object> outputs) {
            this.outputs = outputs != null ? new HashMap<>(outputs) : new HashMap<>();
            return this;
        }

        public Builder providers(Map<String, Object> providers) {
            this.providers = providers != null ? new HashMap<>(providers) : new HashMap<>();
            return this;
        }

        public Builder parseErrors(List<String> parseErrors) {
            this.parseErrors = parseErrors != null ? new ArrayList<>(parseErrors) : new ArrayList<>();
            return this;
        }

        public Builder addParseError(String error) {
            if (error != null && !error.trim().isEmpty()) {
                this.parseErrors.add(error);
            }
            return this;
        }

        public ParsedTerraform build() {
            return new ParsedTerraform(resources, variables, outputs, providers, parseErrors);
        }
    }

    // Getters and setters
    public List<TerraformResource> getResources() {
        return resources;
    }

    public void setResources(List<TerraformResource> resources) {
        this.resources = resources != null ? new ArrayList<>(resources) : new ArrayList<>();
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs != null ? new HashMap<>(outputs) : new HashMap<>();
    }

    public Map<String, Object> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Object> providers) {
        this.providers = providers != null ? new HashMap<>(providers) : new HashMap<>();
    }

    public List<String> getParseErrors() {
        return parseErrors;
    }

    public void setParseErrors(List<String> parseErrors) {
        this.parseErrors = parseErrors != null ? new ArrayList<>(parseErrors) : new ArrayList<>();
    }

    // Utility methods
    public void addResource(TerraformResource resource) {
        if (resource != null) {
            this.resources.add(resource);
        }
    }

    public void addParseError(String error) {
        if (error != null && !error.trim().isEmpty()) {
            this.parseErrors.add(error);
        }
    }

    /**
     * Gets the total count of resources parsed.
     *
     * @return Number of resources
     */
    public int getResourceCount() {
        return resources.size();
    }

    /**
     * Checks if there were any parsing errors.
     *
     * @return true if there are parse errors
     */
    public boolean hasParseErrors() {
        return !parseErrors.isEmpty();
    }

    /**
     * Gets resources by type.
     *
     * @param resourceType The resource type to filter by
     * @return List of resources matching the type
     */
    public List<TerraformResource> getResourcesByType(String resourceType) {
        return resources.stream()
                .filter(resource -> resourceType.equals(resource.getType()))
                .toList();
    }

    /**
     * Checks if parsing was successful (no errors and has resources).
     *
     * @return true if parsing was successful
     */
    public boolean isSuccessful() {
        return !hasParseErrors() && !resources.isEmpty();
    }

    @Override
    public String toString() {
        return "ParsedTerraform{" +
                "resourceCount=" + resources.size() +
                ", variableCount=" + variables.size() +
                ", outputCount=" + outputs.size() +
                ", providerCount=" + providers.size() +
                ", errorCount=" + parseErrors.size() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParsedTerraform that = (ParsedTerraform) o;

        if (resources != null ? !resources.equals(that.resources) : that.resources != null) return false;
        if (variables != null ? !variables.equals(that.variables) : that.variables != null) return false;
        if (outputs != null ? !outputs.equals(that.outputs) : that.outputs != null) return false;
        if (providers != null ? !providers.equals(that.providers) : that.providers != null) return false;
        return parseErrors != null ? parseErrors.equals(that.parseErrors) : that.parseErrors == null;
    }

    @Override
    public int hashCode() {
        int result = resources != null ? resources.hashCode() : 0;
        result = 31 * result + (variables != null ? variables.hashCode() : 0);
        result = 31 * result + (outputs != null ? outputs.hashCode() : 0);
        result = 31 * result + (providers != null ? providers.hashCode() : 0);
        result = 31 * result + (parseErrors != null ? parseErrors.hashCode() : 0);
        return result;
    }
}