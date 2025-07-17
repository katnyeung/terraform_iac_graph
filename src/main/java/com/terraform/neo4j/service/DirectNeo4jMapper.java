package com.terraform.neo4j.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraform.neo4j.model.LLMResource;
import com.terraform.neo4j.model.LLMRelationship;
import com.terraform.neo4j.service.LLMInfrastructureAnalyzer.LLMAnalysisResult;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DirectNeo4jMapper creates Neo4j graphs directly from LLM analysis results,
 * eliminating the need for intermediate object transformations.
 * This service provides simplified graph creation with comprehensive logging.
 */
@Service
@Transactional
public class DirectNeo4jMapper {

    private static final Logger logger = LoggerFactory.getLogger(DirectNeo4jMapper.class);

    private final Driver driver;
    private final ObjectMapper objectMapper;

    public DirectNeo4jMapper(Driver driver) {
        this.driver = driver;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a complete Neo4j graph from LLM analysis results.
     * This is the main entry point for direct graph creation.
     *
     * @param analysis LLM analysis result containing resources and relationships
     */
    public void createGraphFromLLMAnalysis(LLMAnalysisResult analysis) {
        logger.info("Starting direct graph creation from LLM analysis with {} resources and {} relationships",
                analysis.getResources().size(), analysis.getRelationships().size());

        try {
            // 1. Clear existing graph
            clearGraph();

            // 2. Create nodes from LLM-identified resources
            createNodesFromLLMResources(analysis.getResources());

            // 3. Create relationships from LLM analysis
            createRelationshipsFromLLMAnalysis(analysis.getRelationships());

            // 4. Create optimized indexes
            createOptimizedIndexes();

            logger.info("Successfully created direct Neo4j graph with {} nodes and {} relationships",
                    analysis.getResources().size(), analysis.getRelationships().size());

        } catch (Exception e) {
            logger.error("Error creating direct Neo4j graph from LLM analysis", e);
            throw new RuntimeException("Failed to create direct Neo4j graph", e);
        }
    }

    /**
     * Creates Neo4j nodes directly from LLM-identified resources.
     * Each LLMResource becomes a Resource node with properties extracted from LLM analysis.
     *
     * @param resources List of LLM-identified resources
     */
    private void createNodesFromLLMResources(List<LLMResource> resources) {
        logger.info("Creating {} nodes from LLM resources", resources.size());

        for (LLMResource resource : resources) {
            try {
                createResourceNodeFromLLMResource(resource);
            } catch (Exception e) {
                logger.error("Error creating node for LLM resource: {}", resource.getId(), e);
                // Continue with other resources
            }
        }

        logger.info("Completed node creation from LLM resources");
    } 
   /**
     * Creates a single Neo4j Resource node from an LLMResource.
     * Converts LLM-extracted data directly to Neo4j node properties.
     *
     * @param resource LLM-identified resource
     */
    private void createResourceNodeFromLLMResource(LLMResource resource) {
        try (Session session = driver.session()) {
            // Generate unique node ID from Terraform identifier
            String nodeId = generateUniqueNodeId(resource);
            
            // Serialize properties to JSON for storage
            String propertiesJson = serializeResourceProperties(resource.getProperties());
            
            // Determine resource category and provider classification
            String category = categorizeResource(resource.getType());
            String provider = normalizeProvider(resource.getProvider());

            String cypher = """
                CREATE (r:Resource {
                    id: $id,
                    name: $name,
                    type: $type,
                    provider: $provider,
                    category: $category,
                    terraformId: $terraformId,
                    propertiesJson: $propertiesJson,
                    createdBy: 'DIRECT_LLM_ANALYSIS',
                    createdAt: datetime(),
                    lastUpdated: datetime()
                })
                """;

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("id", nodeId);
            parameters.put("name", resource.getName());
            parameters.put("type", resource.getType());
            parameters.put("provider", provider);
            parameters.put("category", category);
            parameters.put("terraformId", resource.getId());
            parameters.put("propertiesJson", propertiesJson);

            session.run(cypher, parameters);
            
            logger.debug("Created Resource node: {} (type: {}, provider: {}, category: {})",
                    nodeId, resource.getType(), provider, category);

        } catch (Exception e) {
            logger.error("Failed to create Resource node for LLM resource: {}", resource.getId(), e);
            throw e;
        }
    }

    /**
     * Creates Neo4j relationships directly from LLM analysis results.
     * Each LLMRelationship becomes a typed relationship with properties.
     *
     * @param relationships List of LLM-identified relationships
     */
    private void createRelationshipsFromLLMAnalysis(List<LLMRelationship> relationships) {
        logger.info("Creating {} relationships from LLM analysis", relationships.size());

        for (LLMRelationship relationship : relationships) {
            try {
                createRelationshipFromLLMRelationship(relationship);
            } catch (Exception e) {
                logger.error("Error creating relationship from {} to {}: {}",
                        relationship.getSource(), relationship.getTarget(), e.getMessage());
                // Continue with other relationships
            }
        }

        logger.info("Completed relationship creation from LLM analysis");
    }

    /**
     * Creates a single Neo4j relationship from an LLMRelationship.
     * Handles relationship type sanitization and robust node matching.
     *
     * @param relationship LLM-identified relationship
     */
    private void createRelationshipFromLLMRelationship(LLMRelationship relationship) {
        try (Session session = driver.session()) {
            logger.debug("Creating relationship: {} -[{}]-> {}",
                    relationship.getSource(), relationship.getType(), relationship.getTarget());

            // Find actual node IDs using robust matching
            String sourceNodeId = findNodeIdByTerraformId(session, relationship.getSource());
            String targetNodeId = findNodeIdByTerraformId(session, relationship.getTarget());

            if (sourceNodeId == null) {
                logger.warn("Cannot create relationship - source node not found: {}", relationship.getSource());
                return;
            }

            if (targetNodeId == null) {
                logger.warn("Cannot create relationship - target node not found: {}", relationship.getTarget());
                return;
            }

            // Sanitize relationship type for Neo4j compatibility
            String sanitizedType = sanitizeRelationshipType(relationship.getType());

            // Create the relationship with properties
            String relationshipCypher = String.format("""
                MATCH (source:Resource {id: $sourceId}), (target:Resource {id: $targetId})
                CREATE (source)-[r:%s {
                    description: $description,
                    confidence: $confidence,
                    originalType: $originalType,
                    createdBy: 'DIRECT_LLM_ANALYSIS',
                    createdAt: datetime()
                }]->(target)
                """, sanitizedType);

            Map<String, Object> relParams = new HashMap<>();
            relParams.put("sourceId", sourceNodeId);
            relParams.put("targetId", targetNodeId);
            relParams.put("description", relationship.getDescription());
            relParams.put("confidence", relationship.getConfidence());
            relParams.put("originalType", relationship.getType());

            session.run(relationshipCypher, relParams);

            logger.debug("Created relationship: {} -[{}]-> {} (confidence: {})",
                    sourceNodeId, sanitizedType, targetNodeId, relationship.getConfidence());

        } catch (Exception e) {
            logger.error("Failed to create relationship from LLM analysis: {} -> {}",
                    relationship.getSource(), relationship.getTarget(), e);
            throw e;
        }
    }

    /**
     * Generates a unique node ID from Terraform resource identifier.
     * Ensures consistent ID generation for the same Terraform resource.
     *
     * @param resource LLM resource
     * @return Unique node ID
     */
    private String generateUniqueNodeId(LLMResource resource) {
        // Use the Terraform ID as the primary identifier
        if (resource.getId() != null && !resource.getId().trim().isEmpty()) {
            return resource.getId().trim();
        }
        
        // Fallback to type.name format
        if (resource.getType() != null && resource.getName() != null) {
            return resource.getType() + "." + resource.getName();
        }
        
        // Last resort: generate from available information
        return "resource_" + System.currentTimeMillis();
    }

    /**
     * Finds a Neo4j node ID by matching against Terraform identifiers.
     * Uses multiple strategies for robust node matching.
     *
     * @param session Neo4j session
     * @param terraformId Terraform resource identifier
     * @return Neo4j node ID or null if not found
     */
    private String findNodeIdByTerraformId(Session session, String terraformId) {
        logger.debug("Looking for node with Terraform ID: {}", terraformId);

        // Strategy 1: Exact match on terraformId field
        Result exactResult = session.run(
                "MATCH (n:Resource) WHERE n.terraformId = $terraformId RETURN n.id as id",
                Map.of("terraformId", terraformId));
        if (exactResult.hasNext()) {
            String foundId = exactResult.next().get("id").asString();
            logger.debug("Found exact terraformId match: {}", foundId);
            return foundId;
        }

        // Strategy 2: Exact match on id field
        Result idResult = session.run(
                "MATCH (n:Resource) WHERE n.id = $id RETURN n.id as id",
                Map.of("id", terraformId));
        if (idResult.hasNext()) {
            String foundId = idResult.next().get("id").asString();
            logger.debug("Found exact id match: {}", foundId);
            return foundId;
        }

        // Strategy 3: Partial match for complex identifiers
        if (terraformId.contains(".")) {
            String[] parts = terraformId.split("\\.", 2);
            String resourceType = parts[0];
            String resourceName = parts[1];

            Result partialResult = session.run("""
                MATCH (n:Resource) 
                WHERE n.type = $type AND n.name = $name 
                RETURN n.id as id LIMIT 1
                """, Map.of("type", resourceType, "name", resourceName));
            
            if (partialResult.hasNext()) {
                String foundId = partialResult.next().get("id").asString();
                logger.debug("Found partial match for type '{}' and name '{}': {}", resourceType, resourceName, foundId);
                return foundId;
            }
        }

        // Strategy 4: Module reference handling
        if (terraformId.startsWith("module.")) {
            String moduleName = terraformId.substring(7); // Remove "module."
            Result moduleResult = session.run("""
                MATCH (n:Resource) 
                WHERE n.type CONTAINS $moduleName OR n.name CONTAINS $moduleName 
                RETURN n.id as id LIMIT 1
                """, Map.of("moduleName", moduleName));
            
            if (moduleResult.hasNext()) {
                String foundId = moduleResult.next().get("id").asString();
                logger.debug("Found module match for '{}': {}", moduleName, foundId);
                return foundId;
            }
        }

        logger.debug("No match found for Terraform ID: {}", terraformId);
        return null;
    }

    /**
     * Sanitizes relationship type for Neo4j compatibility.
     * Neo4j relationship types cannot contain spaces or special characters.
     *
     * @param type Original relationship type
     * @return Sanitized relationship type
     */
    private String sanitizeRelationshipType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "RELATED_TO";
        }
        
        // Replace spaces and special characters with underscores
        return type.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9_]", "_")
                .replaceAll("_{2,}", "_") // Replace multiple underscores with single
                .replaceAll("^_|_$", ""); // Remove leading/trailing underscores
    }

    /**
     * Serializes resource properties to JSON string for storage.
     * Handles null properties and serialization errors gracefully.
     *
     * @param properties Resource properties map
     * @return JSON string representation
     */
    private String serializeResourceProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize resource properties to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Categorizes resources by type for better organization.
     * Provides logical grouping of infrastructure components.
     *
     * @param resourceType Terraform resource type
     * @return Resource category
     */
    private String categorizeResource(String resourceType) {
        if (resourceType == null) return "unknown";

        String lowerType = resourceType.toLowerCase();

        if (lowerType.contains("instance") || lowerType.contains("vm") ||
                lowerType.contains("compute") || lowerType.contains("lambda") ||
                lowerType.contains("eks") || lowerType.contains("cluster")) {
            return "compute";
        } else if (lowerType.contains("bucket") || lowerType.contains("disk") ||
                lowerType.contains("volume") || lowerType.contains("efs") ||
                lowerType.contains("storage")) {
            return "storage";
        } else if (lowerType.contains("db") || lowerType.contains("database") ||
                lowerType.contains("sql") || lowerType.contains("rds")) {
            return "database";
        } else if (lowerType.contains("vpc") || lowerType.contains("subnet") ||
                lowerType.contains("gateway") || lowerType.contains("lb") ||
                lowerType.contains("security_group") || lowerType.contains("route")) {
            return "network";
        } else if (lowerType.contains("iam") || lowerType.contains("role") ||
                lowerType.contains("policy") || lowerType.contains("user")) {
            return "identity";
        } else if (lowerType.contains("helm") || lowerType.contains("kubernetes") ||
                lowerType.contains("deployment") || lowerType.contains("service")) {
            return "application";
        }

        return "other";
    }

    /**
     * Normalizes provider names for consistency.
     * Ensures consistent provider classification across resources.
     *
     * @param provider Original provider name
     * @return Normalized provider name
     */
    private String normalizeProvider(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            return "unknown";
        }
        
        return provider.trim().toLowerCase();
    }

    /**
     * Clears the entire Neo4j graph.
     * Removes all nodes and relationships to prepare for fresh data.
     */
    public void clearGraph() {
        logger.info("Clearing existing Neo4j graph data");

        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
            logger.info("Neo4j graph cleared successfully");
        } catch (Exception e) {
            logger.error("Error clearing Neo4j graph", e);
            throw new RuntimeException("Failed to clear Neo4j graph", e);
        }
    }

    /**
     * Creates optimized indexes for better query performance.
     * Indexes are created on commonly queried properties.
     */
    private void createOptimizedIndexes() {
        logger.debug("Creating optimized Neo4j indexes");

        try (Session session = driver.session()) {
            // Resource node indexes
            session.run("CREATE INDEX resource_id_idx IF NOT EXISTS FOR (r:Resource) ON (r.id)");
            session.run("CREATE INDEX resource_terraform_id_idx IF NOT EXISTS FOR (r:Resource) ON (r.terraformId)");
            session.run("CREATE INDEX resource_type_idx IF NOT EXISTS FOR (r:Resource) ON (r.type)");
            session.run("CREATE INDEX resource_provider_idx IF NOT EXISTS FOR (r:Resource) ON (r.provider)");
            session.run("CREATE INDEX resource_category_idx IF NOT EXISTS FOR (r:Resource) ON (r.category)");
            session.run("CREATE INDEX resource_name_idx IF NOT EXISTS FOR (r:Resource) ON (r.name)");

            logger.debug("Optimized Neo4j indexes created successfully");
        } catch (Exception e) {
            logger.warn("Some indexes may already exist: {}", e.getMessage());
        }
    }

    /**
     * Gets comprehensive statistics about the created graph.
     * Provides insights into graph structure and content.
     *
     * @return Map containing graph statistics
     */
    public Map<String, Object> getGraphStatistics() {
        try (Session session = driver.session()) {
            Map<String, Object> stats = new HashMap<>();

            // Count total nodes and relationships
            Result totalResult = session.run("""
                MATCH (n) 
                OPTIONAL MATCH ()-[r]->() 
                RETURN count(DISTINCT n) as nodeCount, count(r) as relationshipCount
                """);
            
            if (totalResult.hasNext()) {
                Record record = totalResult.next();
                stats.put("totalNodes", record.get("nodeCount").asInt());
                stats.put("totalRelationships", record.get("relationshipCount").asInt());
            }

            // Count nodes by category
            Result categoryResult = session.run("""
                MATCH (n:Resource) 
                RETURN n.category as category, count(n) as count 
                ORDER BY count DESC
                """);
            
            Map<String, Integer> categoryCounts = new HashMap<>();
            while (categoryResult.hasNext()) {
                Record record = categoryResult.next();
                String category = record.get("category").asString();
                int count = record.get("count").asInt();
                categoryCounts.put(category, count);
            }
            stats.put("nodesByCategory", categoryCounts);

            // Count nodes by provider
            Result providerResult = session.run("""
                MATCH (n:Resource) 
                RETURN n.provider as provider, count(n) as count 
                ORDER BY count DESC
                """);
            
            Map<String, Integer> providerCounts = new HashMap<>();
            while (providerResult.hasNext()) {
                Record record = providerResult.next();
                String provider = record.get("provider").asString();
                int count = record.get("count").asInt();
                providerCounts.put(provider, count);
            }
            stats.put("nodesByProvider", providerCounts);

            // Count relationships by type
            Result relTypeResult = session.run("""
                MATCH ()-[r]->() 
                RETURN type(r) as relationshipType, count(r) as count 
                ORDER BY count DESC
                """);
            
            Map<String, Integer> relationshipCounts = new HashMap<>();
            while (relTypeResult.hasNext()) {
                Record record = relTypeResult.next();
                String relType = record.get("relationshipType").asString();
                int count = record.get("count").asInt();
                relationshipCounts.put(relType, count);
            }
            stats.put("relationshipsByType", relationshipCounts);

            return stats;

        } catch (Exception e) {
            logger.error("Error getting graph statistics", e);
            return Map.of("error", e.getMessage());
        }
    }
}