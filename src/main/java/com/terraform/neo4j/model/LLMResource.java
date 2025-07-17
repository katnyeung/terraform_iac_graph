package com.terraform.neo4j.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Model representing a resource identified by LLM from Terraform configuration.
 * This class encapsulates resource information extracted from LLM analysis.
 */
public class LLMResource {
    private String id;
    private String type;
    private String name;
    private String provider;
    private Map<String, Object> properties;

    public LLMResource() {
        this.properties = new HashMap<>();
    }

    public LLMResource(String id, String type, String name, String provider) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.provider = provider;
        this.properties = new HashMap<>();
    }

    /**
     * Creates LLMResource from a Map (typically from JSON parsing)
     */
    public static LLMResource fromMap(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Map cannot be null");
        }

        LLMResource resource = new LLMResource();
        resource.id = (String) map.get("id");
        resource.type = (String) map.get("type");
        resource.name = (String) map.get("name");
        resource.provider = (String) map.get("provider");
        resource.properties = map.containsKey("properties") ?
                (Map<String, Object>) map.get("properties") : new HashMap<>();

        // Validate required fields
        if (resource.id == null || resource.id.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource ID cannot be null or empty");
        }
        if (resource.type == null || resource.type.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource type cannot be null or empty");
        }

        return resource;
    }

    /**
     * Builder pattern for easy object construction
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String type;
        private String name;
        private String provider;
        private Map<String, Object> properties = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
            return this;
        }

        public Builder addProperty(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public LLMResource build() {
            LLMResource resource = new LLMResource(id, type, name, provider);
            resource.setProperties(properties);
            return resource;
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties != null ? properties : new HashMap<>();
    }

    /**
     * Add a property to the resource
     */
    public void addProperty(String key, Object value) {
        if (this.properties == null) {
            this.properties = new HashMap<>();
        }
        this.properties.put(key, value);
    }

    /**
     * Get a property value
     */
    public Object getProperty(String key) {
        return properties != null ? properties.get(key) : null;
    }

    /**
     * Check if resource has a specific property
     */
    public boolean hasProperty(String key) {
        return properties != null && properties.containsKey(key);
    }

    /**
     * Validation method to check if resource is valid
     */
    public boolean isValid() {
        return id != null && !id.trim().isEmpty() &&
               type != null && !type.trim().isEmpty();
    }

    /**
     * Get resource identifier in Terraform format (type.name)
     */
    public String getTerraformIdentifier() {
        if (type != null && name != null) {
            return type + "." + name;
        }
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LLMResource that = (LLMResource) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(type, that.type) &&
               Objects.equals(name, that.name) &&
               Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, name, provider);
    }

    @Override
    public String toString() {
        return String.format("LLMResource{id='%s', type='%s', name='%s', provider='%s', properties=%d}",
                id, type, name, provider, properties != null ? properties.size() : 0);
    }
}