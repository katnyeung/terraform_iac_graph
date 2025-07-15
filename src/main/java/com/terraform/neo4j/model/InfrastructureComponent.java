package com.terraform.neo4j.model;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Data model representing an infrastructure component extracted from Terraform resources.
 * Contains metadata about the component including provider, identity type, and all properties.
 */
public class InfrastructureComponent {
    private String id;          // Generated unique ID
    private String name;        // Resource name
    private String type;        // Resource type
    private String provider;    // GCP, AWS, Azure (detected from type)
    private IdentityType identityType; // IDENTITY_RESOURCE or REGULAR_RESOURCE
    private Map<String, Object> properties; // All configuration properties

    // Default constructor
    public InfrastructureComponent() {
        this.properties = new HashMap<>();
    }

    // Constructor with all fields
    public InfrastructureComponent(String id, String name, String type, String provider, 
                                 IdentityType identityType, Map<String, Object> properties) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.provider = provider;
        this.identityType = identityType;
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String type;
        private String provider;
        private IdentityType identityType;
        private Map<String, Object> properties = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder identityType(IdentityType identityType) {
            this.identityType = identityType;
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

        public InfrastructureComponent build() {
            return new InfrastructureComponent(id, name, type, provider, identityType, properties);
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public IdentityType getIdentityType() {
        return identityType;
    }

    public void setIdentityType(IdentityType identityType) {
        this.identityType = identityType;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    /**
     * Gets a specific property value by key.
     *
     * @param key The property key
     * @return The property value, or null if not found
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Adds or updates a property.
     *
     * @param key The property key
     * @param value The property value
     */
    public void addProperty(String key, Object value) {
        this.properties.put(key, value);
    }

    /**
     * Checks if this component is an identity resource.
     *
     * @return true if this is an identity resource, false otherwise
     */
    public boolean isIdentityResource() {
        return identityType == IdentityType.IDENTITY_RESOURCE;
    }

    @Override
    public String toString() {
        return "InfrastructureComponent{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", provider='" + provider + '\'' +
                ", identityType=" + identityType +
                ", propertyCount=" + (properties != null ? properties.size() : 0) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InfrastructureComponent that = (InfrastructureComponent) o;

        return Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(type, that.type) &&
               Objects.equals(provider, that.provider) &&
               identityType == that.identityType &&
               Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, provider, identityType, properties);
    }
}