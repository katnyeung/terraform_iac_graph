package com.terraform.neo4j.model;

import java.util.Map;
import java.util.HashMap;

/**
 * Data model representing a Terraform resource extracted from HCL configuration.
 * Contains the resource type, name, and all configuration arguments.
 */
public class TerraformResource {
    private String type;        // e.g., "google_compute_instance"
    private String name;        // e.g., "web_server"
    private Map<String, Object> arguments; // All resource configuration

    // Default constructor
    public TerraformResource() {
        this.arguments = new HashMap<>();
    }

    // Constructor with all fields
    public TerraformResource(String type, String name, Map<String, Object> arguments) {
        this.type = type;
        this.name = name;
        this.arguments = arguments != null ? new HashMap<>(arguments) : new HashMap<>();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String type;
        private String name;
        private Map<String, Object> arguments = new HashMap<>();

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments != null ? new HashMap<>(arguments) : new HashMap<>();
            return this;
        }

        public Builder addArgument(String key, Object value) {
            this.arguments.put(key, value);
            return this;
        }

        public TerraformResource build() {
            return new TerraformResource(type, name, arguments);
        }
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments != null ? new HashMap<>(arguments) : new HashMap<>();
    }

    /**
     * Gets a specific argument value by key.
     *
     * @param key The argument key
     * @return The argument value, or null if not found
     */
    public Object getArgument(String key) {
        return arguments.get(key);
    }

    /**
     * Adds or updates an argument.
     *
     * @param key The argument key
     * @param value The argument value
     */
    public void addArgument(String key, Object value) {
        this.arguments.put(key, value);
    }

    /**
     * Gets the full resource identifier in Terraform format.
     *
     * @return Resource identifier as "type.name"
     */
    public String getResourceId() {
        return type + "." + name;
    }

    @Override
    public String toString() {
        return "TerraformResource{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", argumentCount=" + (arguments != null ? arguments.size() : 0) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TerraformResource that = (TerraformResource) o;

        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return arguments != null ? arguments.equals(that.arguments) : that.arguments == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
        return result;
    }
}