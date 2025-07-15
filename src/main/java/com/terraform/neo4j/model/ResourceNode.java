package com.terraform.neo4j.model;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.util.Map;
import java.util.Objects;

/**
 * Neo4j entity representing a Terraform resource node in the graph database.
 * Each resource from Terraform configuration becomes a node with properties
 * containing all the configuration details.
 */
@Node("Resource")
public class ResourceNode {

    @Id
    @GeneratedValue
    private Long nodeId;

    @Property("id")
    private String id;

    @Property("name")
    private String name;

    @Property("type")
    private String type;

    @Property("provider")
    private String provider;

    @Property("identityType")
    private String identityType;

    @Property("properties")
    private Map<String, Object> properties;

    // Default constructor for Neo4j
    public ResourceNode() {}

    // Constructor for creating nodes
    public ResourceNode(String id, String name, String type, String provider, 
                       String identityType, Map<String, Object> properties) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.provider = provider;
        this.identityType = identityType;
        this.properties = properties;
    }

    // Getters and setters
    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
    }

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

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceNode that = (ResourceNode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ResourceNode{" +
                "nodeId=" + nodeId +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", provider='" + provider + '\'' +
                ", identityType='" + identityType + '\'' +
                ", properties=" + properties +
                '}';
    }
}