package com.terraform.neo4j.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraform.neo4j.model.InfrastructureComponent;
import com.terraform.neo4j.model.ResourceNode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for mapping Terraform infrastructure components
 * to Neo4j graph database nodes and relationships.
 */
@Service
@Transactional
public class SimpleNeo4jMapper {

    private static final Logger logger = LoggerFactory.getLogger(SimpleNeo4jMapper.class);

    private final Neo4jTemplate neo4jTemplate;
    private final Driver driver;
    private final ObjectMapper objectMapper;

    public SimpleNeo4jMapper(Neo4jTemplate neo4jTemplate, Driver driver) {
        this.neo4jTemplate = neo4jTemplate;
        this.driver = driver;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Maps a list of infrastructure components to Neo4j graph nodes.
     * This method clears existing data, creates new nodes, and sets up basic indexes.
     *
     * @param components List of infrastructure components to map
     */
    public void mapToGraph(List<InfrastructureComponent> components) {
        logger.info("Starting to map {} components to Neo4j graph", components.size());

        try {
            // Clear existing data
            clearGraph();

            // Create nodes
            createNodes(components);

            // Create basic indexes for performance
            createIndexes();

            logger.info("Successfully mapped {} components to Neo4j graph", components.size());
        } catch (Exception e) {
            logger.error("Error mapping components to Neo4j graph", e);
            throw new RuntimeException("Failed to map components to Neo4j graph", e);
        }
    }

    /**
     * Creates Neo4j nodes from infrastructure components.
     * Handles property serialization for complex configurations and duplicate resource updates.
     *
     * @param components List of components to create nodes for
     */
    private void createNodes(List<InfrastructureComponent> components) {
        logger.debug("Creating {} nodes in Neo4j", components.size());

        for (InfrastructureComponent component : components) {
            try {
                // Serialize complex properties to JSON strings
                Map<String, Object> serializedProperties = serializeProperties(component.getProperties());
                
                // Check if node already exists and update or create accordingly
                createOrUpdateNode(component, serializedProperties);
                
                logger.debug("Created/updated node for component: {}", component.getId());
            } catch (Exception e) {
                logger.error("Error creating/updating node for component: {}", component.getId(), e);
                throw new RuntimeException("Failed to create/update node for component: " + component.getId(), e);
            }
        }
    }

    /**
     * Clears all existing Resource nodes from the Neo4j database.
     * This ensures a clean state for new imports.
     */
    public void clearGraph() {
        logger.info("Clearing existing Resource nodes from Neo4j graph");

        try (Session session = driver.session()) {
            String cypher = "MATCH (n:Resource) DETACH DELETE n";
            session.run(cypher);
            logger.info("Successfully cleared all Resource nodes");
        } catch (Exception e) {
            logger.error("Error clearing Neo4j graph", e);
            throw new RuntimeException("Failed to clear Neo4j graph", e);
        }
    }

    /**
     * Creates basic indexes on Resource nodes for improved query performance.
     */
    private void createIndexes() {
        logger.debug("Creating indexes for Resource nodes");

        try {
            // Create indexes for commonly queried properties
            createIndexIfNotExists("resource_id", "Resource", "id");
            createIndexIfNotExists("resource_type", "Resource", "type");
            createIndexIfNotExists("resource_provider", "Resource", "provider");
            createIndexIfNotExists("resource_identity_type", "Resource", "identityType");

            logger.debug("Successfully created indexes");
        } catch (Exception e) {
            logger.warn("Error creating indexes (this may be normal if they already exist): {}", e.getMessage());
        }
    }

    /**
     * Creates an index if it doesn't already exist.
     *
     * @param indexName Name of the index
     * @param nodeLabel Label of the node
     * @param property Property to index
     */
    private void createIndexIfNotExists(String indexName, String nodeLabel, String property) {
        try (Session session = driver.session()) {
            String cypher = String.format("CREATE INDEX %s IF NOT EXISTS FOR (n:%s) ON (n.%s)", 
                                        indexName, nodeLabel, property);
            session.run(cypher);
        } catch (Exception e) {
            logger.debug("Index {} may already exist: {}", indexName, e.getMessage());
        }
    }

    /**
     * Initializes the Neo4j database with any required setup.
     * This method can be called during application startup.
     */
    public void initializeDatabase() {
        logger.info("Initializing Neo4j database");

        try {
            // Test connection
            testConnection();

            // Create indexes
            createIndexes();

            logger.info("Neo4j database initialization completed successfully");
        } catch (Exception e) {
            logger.error("Error initializing Neo4j database", e);
            throw new RuntimeException("Failed to initialize Neo4j database", e);
        }
    }

    /**
     * Tests the Neo4j database connection.
     */
    private void testConnection() {
        try (Session session = driver.session()) {
            String cypher = "RETURN 1 as test";
            Result result = session.run(cypher);
            
            if (!result.hasNext()) {
                throw new RuntimeException("Connection test failed");
            }
            
            Record record = result.next();
            if (record.get("test").asInt() != 1) {
                throw new RuntimeException("Connection test failed");
            }
            
            logger.debug("Neo4j connection test successful");
        } catch (Exception e) {
            logger.error("Neo4j connection test failed", e);
            throw new RuntimeException("Neo4j connection test failed", e);
        }
    }

    /**
     * Gets the count of Resource nodes in the database.
     *
     * @return Number of Resource nodes
     */
    public long getResourceCount() {
        try (Session session = driver.session()) {
            String cypher = "MATCH (n:Resource) RETURN count(n) as count";
            Result result = session.run(cypher);
            
            if (!result.hasNext()) {
                return 0L;
            }
            
            Record record = result.next();
            return record.get("count").asLong();
        } catch (Exception e) {
            logger.error("Error getting resource count", e);
            return 0L;
        }
    }

    /**
     * Serializes complex Terraform configuration properties to JSON strings.
     * This handles nested objects, arrays, and other complex data structures.
     *
     * @param properties Original properties map
     * @return Serialized properties map with complex objects as JSON strings
     */
    private Map<String, Object> serializeProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> serializedProperties = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value == null) {
                serializedProperties.put(key, null);
            } else if (isSimpleType(value)) {
                // Keep simple types as-is
                serializedProperties.put(key, value);
            } else {
                // Serialize complex objects to JSON strings
                try {
                    String jsonValue = objectMapper.writeValueAsString(value);
                    serializedProperties.put(key, jsonValue);
                    logger.debug("Serialized complex property '{}' to JSON", key);
                } catch (JsonProcessingException e) {
                    logger.warn("Failed to serialize property '{}', storing as string: {}", key, e.getMessage());
                    serializedProperties.put(key, value.toString());
                }
            }
        }
        
        return serializedProperties;
    }

    /**
     * Checks if a value is a simple type that can be stored directly in Neo4j.
     * Neo4j supports primitives and arrays of primitives.
     *
     * @param value Value to check
     * @return true if the value is a simple type
     */
    private boolean isSimpleType(Object value) {
        if (value == null) {
            return true;
        }
        
        // Basic primitive types
        if (value instanceof String ||
            value instanceof Number ||
            value instanceof Boolean) {
            return true;
        }
        
        // Arrays of primitive types
        if (value instanceof String[] ||
            value instanceof Number[] ||
            value instanceof Boolean[] ||
            value instanceof int[] ||
            value instanceof long[] ||
            value instanceof double[] ||
            value instanceof float[] ||
            value instanceof boolean[]) {
            return true;
        }
        
        // Lists of primitive types
        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            if (list.isEmpty()) {
                return true; // Empty list is safe
            }
            // Check if all elements are primitive types
            return list.stream().allMatch(item -> 
                item instanceof String || 
                item instanceof Number || 
                item instanceof Boolean);
        }
        
        return false;
    }

    /**
     * Creates a new node or updates an existing node if it already exists.
     * This handles duplicate resources by updating existing nodes with new properties.
     *
     * @param component Infrastructure component
     * @param serializedProperties Serialized properties map
     */
    private void createOrUpdateNode(InfrastructureComponent component, Map<String, Object> serializedProperties) {
        try (Session session = driver.session()) {
            // Convert the entire properties map to JSON string to avoid nested map issues
            String propertiesJson;
            try {
                propertiesJson = objectMapper.writeValueAsString(serializedProperties);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize properties for component {}, using empty JSON", component.getId());
                propertiesJson = "{}";
            }
            
            // Use MERGE to create or update the node
            String cypher = """
                MERGE (n:Resource {id: $id})
                SET n.name = $name,
                    n.type = $type,
                    n.provider = $provider,
                    n.identityType = $identityType,
                    n.propertiesJson = $propertiesJson,
                    n.lastUpdated = datetime()
                RETURN n.id as nodeId
                """;
            
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("id", component.getId());
            parameters.put("name", component.getName());
            parameters.put("type", component.getType());
            parameters.put("provider", component.getProvider());
            parameters.put("identityType", component.getIdentityType().toString());
            parameters.put("propertiesJson", propertiesJson);
            
            Result result = session.run(cypher, parameters);
            
            if (result.hasNext()) {
                Record record = result.next();
                String nodeId = record.get("nodeId").asString();
                logger.debug("Successfully created/updated node with ID: {}", nodeId);
            } else {
                logger.warn("No result returned when creating/updating node for component: {}", component.getId());
            }
            
        } catch (Exception e) {
            logger.error("Error creating/updating node for component: {}", component.getId(), e);
            throw new RuntimeException("Failed to create/update node for component: " + component.getId(), e);
        }
    }

    /**
     * Maps components to graph without clearing existing data.
     * This method allows for incremental updates to the graph.
     *
     * @param components List of infrastructure components to map
     */
    public void mapToGraphIncremental(List<InfrastructureComponent> components) {
        logger.info("Starting incremental mapping of {} components to Neo4j graph", components.size());

        try {
            // Create/update nodes without clearing existing data
            createNodes(components);

            // Ensure indexes exist
            createIndexes();

            logger.info("Successfully mapped {} components to Neo4j graph incrementally", components.size());
        } catch (Exception e) {
            logger.error("Error mapping components to Neo4j graph incrementally", e);
            throw new RuntimeException("Failed to map components to Neo4j graph incrementally", e);
        }
    }

    /**
     * Checks if a resource node exists in the database.
     *
     * @param resourceId Resource ID to check
     * @return true if the resource exists
     */
    public boolean resourceExists(String resourceId) {
        try (Session session = driver.session()) {
            String cypher = "MATCH (n:Resource {id: $id}) RETURN count(n) as count";
            Result result = session.run(cypher, Map.of("id", resourceId));
            
            if (!result.hasNext()) {
                return false;
            }
            
            Record record = result.next();
            return record.get("count").asLong() > 0;
        } catch (Exception e) {
            logger.error("Error checking if resource exists: {}", resourceId, e);
            return false;
        }
    }
}