package com.terraform.neo4j.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraform.neo4j.model.InfrastructureComponent;
import com.terraform.neo4j.service.LLMInfrastructureAnalyzer.InfrastructureRelationship;
import com.terraform.neo4j.service.LLMInfrastructureAnalyzer.InfrastructureInsight;
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
 * Enhanced Neo4j mapper that creates intelligent graph structures based on
 * LLM analysis of Terraform infrastructure.
 */
@Service
@Transactional
public class IntelligentNeo4jMapper {

    private static final Logger logger = LoggerFactory.getLogger(IntelligentNeo4jMapper.class);

    private final Driver driver;
    private final ObjectMapper objectMapper;

    public IntelligentNeo4jMapper(Driver driver) {
        this.driver = driver;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Maps infrastructure components to Neo4j graph with LLM-derived relationships.
     *
     * @param components List of infrastructure components
     * @param llmAnalysis LLM analysis result with relationships and insights
     */
    public void mapToIntelligentGraph(List<InfrastructureComponent> components, LLMAnalysisResult llmAnalysis) {
        logger.info("Starting intelligent mapping of {} components with {} relationships to Neo4j",
                components.size(), llmAnalysis.getRelationships().size());

        // Log LLM analysis for debugging
        logger.debug("LLM Analysis Summary: {}", llmAnalysis.getSummary());
        logger.debug("LLM Relationships: {}", llmAnalysis.getRelationships());
        logger.debug("LLM Insights: {}", llmAnalysis.getInsights());

        try {
            // Clear existing data
            clearGraph();

            // Create resource nodes
            createResourceNodes(components);

            // Log all created nodes for debugging relationship issues
            logCreatedNodes();

            // Create LLM-derived relationships
            createIntelligentRelationships(llmAnalysis.getRelationships());

            // Store architectural insights
            storeArchitecturalInsights(llmAnalysis.getInsights(), llmAnalysis.getSummary());

            // Create indexes for performance
            createIntelligentIndexes();

            logger.info("Successfully created intelligent graph with {} nodes, {} relationships, and {} insights",
                    components.size(), llmAnalysis.getRelationships().size(), llmAnalysis.getInsights().size());

        } catch (Exception e) {
            logger.error("Error creating intelligent Neo4j graph", e);
            throw new RuntimeException("Failed to create intelligent Neo4j graph", e);
        }
    }

    /**
     * Creates resource nodes with enhanced metadata.
     */
    private void createResourceNodes(List<InfrastructureComponent> components) {
        logger.debug("Creating {} resource nodes", components.size());

        // Create virtual EKS cluster node if EKS-related components exist
        createVirtualEKSClusterNode(components);

        for (InfrastructureComponent component : components) {
            try {
                createEnhancedResourceNode(component);
            } catch (Exception e) {
                logger.error("Error creating node for component: {}", component.getId(), e);
                // Continue with other components
            }
        }
    }

    /**
     * Creates a virtual EKS cluster node to represent the module.eks reference.
     */
    private void createVirtualEKSClusterNode(List<InfrastructureComponent> components) {
        // Check if we have Helm releases (which would deploy on EKS)
        boolean hasHelmReleases = components.stream()
                .anyMatch(c -> "helm_release".equals(c.getType()));

        if (hasHelmReleases) {
            try (Session session = driver.session()) {
                String cypher = """
                    CREATE (r:Resource {
                        id: $id,
                        name: $name,
                        type: $type,
                        provider: $provider,
                        identityType: $identityType,
                        propertiesJson: $propertiesJson,
                        category: $category,
                        isVirtual: true,
                        createdAt: datetime(),
                        lastUpdated: datetime()
                    })
                    """;

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("id", "module.eks");
                parameters.put("name", "eks");
                parameters.put("type", "aws_eks_cluster");
                parameters.put("provider", "aws");
                parameters.put("identityType", "REGULAR_RESOURCE");
                parameters.put("propertiesJson", "{\"cluster_name\":\"ai_impact\",\"description\":\"Virtual EKS cluster node representing module.eks\"}");
                parameters.put("category", "compute");

                session.run(cypher, parameters);
                logger.debug("Created virtual EKS cluster node: module.eks");
            } catch (Exception e) {
                logger.warn("Failed to create virtual EKS cluster node: {}", e.getMessage());
            }
        }
    }

    /**
     * Creates a single resource node with enhanced properties.
     */
    private void createEnhancedResourceNode(InfrastructureComponent component) {
        try (Session session = driver.session()) {
            // Sanitize and serialize properties to JSON
            String propertiesJson;
            try {
                Map<String, Object> sanitizedProperties = sanitizeProperties(component.getProperties());
                propertiesJson = objectMapper.writeValueAsString(sanitizedProperties);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize properties for component {}: {}", component.getId(), e.getMessage());
                propertiesJson = "{}";
            }

            String cypher = """
                CREATE (r:Resource {
                    id: $id,
                    name: $name,
                    type: $type,
                    provider: $provider,
                    identityType: $identityType,
                    propertiesJson: $propertiesJson,
                    category: $category,
                    createdAt: datetime(),
                    lastUpdated: datetime()
                })
                """;

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("id", component.getId());
            parameters.put("name", component.getName());
            parameters.put("type", component.getType());
            parameters.put("provider", component.getProvider());
            parameters.put("identityType", component.getIdentityType().toString());
            parameters.put("propertiesJson", propertiesJson);
            parameters.put("category", categorizeResource(component.getType()));

            session.run(cypher, parameters);
            logger.debug("Created enhanced node for: {}", component.getId());
        }
    }

    /**
     * Logs all created nodes for debugging relationship issues.
     */
    private void logCreatedNodes() {
        try (Session session = driver.session()) {
            Result result = session.run("MATCH (n:Resource) RETURN n.id as id, n.type as type, n.name as name ORDER BY n.id");

            logger.debug("=== Created Nodes in Neo4j ===");
            while (result.hasNext()) {
                Record record = result.next();
                String id = record.get("id").asString();
                String type = record.get("type").asString();
                String name = record.get("name").asString();
                logger.debug("Node: {} (type: {}, name: {})", id, type, name);
            }
            logger.debug("=== End of Created Nodes ===");

        } catch (Exception e) {
            logger.warn("Failed to log created nodes: {}", e.getMessage());
        }
    }

    /**
     * Creates intelligent relationships based on LLM analysis.
     */
    private void createIntelligentRelationships(List<InfrastructureRelationship> relationships) {
        logger.debug("Creating {} intelligent relationships", relationships.size());

        for (InfrastructureRelationship relationship : relationships) {
            try {
                createRelationship(relationship);
            } catch (Exception e) {
                logger.error("Error creating relationship from {} to {}: {}",
                        relationship.getSource(), relationship.getTarget(), e.getMessage());
                // Continue with other relationships
            }
        }
    }

    /**
     * Creates a single relationship between resources.
     */
    private void createRelationship(InfrastructureRelationship relationship) {
        try (Session session = driver.session()) {
            logger.debug("Attempting to create relationship: {} -> {}", relationship.getSource(), relationship.getTarget());

            // Try multiple strategies to find the nodes
            String actualSourceId = findNodeId(session, relationship.getSource());
            String actualTargetId = findNodeId(session, relationship.getTarget());

            if (actualSourceId == null) {
                logger.warn("Cannot create relationship - source node not found: {}", relationship.getSource());
                return;
            }

            if (actualTargetId == null) {
                logger.warn("Cannot create relationship - target node not found: {}", relationship.getTarget());
                return;
            }

            // Create the relationship
            String relationshipCypher = String.format("""
                MATCH (source:Resource {id: $sourceId}), (target:Resource {id: $targetId})
                CREATE (source)-[r:%s {
                    description: $description,
                    confidence: $confidence,
                    createdBy: 'LLM_ANALYSIS',
                    createdAt: datetime()
                }]->(target)
                """, sanitizeRelationshipType(relationship.getType()));

            Map<String, Object> relParams = new HashMap<>();
            relParams.put("sourceId", actualSourceId);
            relParams.put("targetId", actualTargetId);
            relParams.put("description", relationship.getDescription());
            relParams.put("confidence", relationship.getConfidence());

            session.run(relationshipCypher, relParams);

            logger.debug("Created relationship: {} -[{}]-> {}",
                    actualSourceId, relationship.getType(), actualTargetId);
        }
    }

    /**
     * Finds a node ID using multiple matching strategies.
     */
    private String findNodeId(Session session, String identifier) {
        logger.debug("Looking for node with identifier: {}", identifier);

        // Strategy 1: Exact match
        Result exactResult = session.run("MATCH (n:Resource) WHERE n.id = $id RETURN n.id as id",
                Map.of("id", identifier));
        if (exactResult.hasNext()) {
            String foundId = exactResult.next().get("id").asString();
            logger.debug("Found exact match: {}", foundId);
            return foundId;
        }

        // Strategy 2: Contains match (for cases like "helm_release.cert_manager")
        String resourceName = extractResourceName(identifier);
        Result containsResult = session.run("MATCH (n:Resource) WHERE n.id CONTAINS $name RETURN n.id as id",
                Map.of("name", resourceName));
        if (containsResult.hasNext()) {
            String foundId = containsResult.next().get("id").asString();
            logger.debug("Found contains match for '{}': {}", resourceName, foundId);
            return foundId;
        }

        // Strategy 3: Type and name match (for cases like "module.eks" -> look for EKS-related resources)
        if (identifier.startsWith("module.")) {
            String moduleName = identifier.substring(7); // Remove "module."
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

        // Strategy 4: Fuzzy match by resource type
        if (identifier.contains(".")) {
            String resourceType = identifier.split("\\.")[0];
            Result typeResult = session.run("MATCH (n:Resource) WHERE n.type = $type RETURN n.id as id LIMIT 1",
                    Map.of("type", resourceType));
            if (typeResult.hasNext()) {
                String foundId = typeResult.next().get("id").asString();
                logger.debug("Found type match for '{}': {}", resourceType, foundId);
                return foundId;
            }
        }

        logger.debug("No match found for identifier: {}", identifier);
        return null;
    }

    /**
     * Stores architectural insights as separate nodes.
     */
    private void storeArchitecturalInsights(List<InfrastructureInsight> insights,
                                            LLMInfrastructureAnalyzer.ArchitectureSummary summary) {
        logger.debug("Storing {} architectural insights", insights.size());

        try (Session session = driver.session()) {
            // Store overall architecture summary
            if (summary != null) {
                String summaryCypher = """
                    CREATE (arch:ArchitectureSummary {
                        type: $architectureType,
                        complexity: $complexity,
                        purpose: $mainPurpose,
                        analyzedAt: datetime()
                    })
                    """;

                Map<String, Object> summaryParams = new HashMap<>();
                summaryParams.put("architectureType", summary.getArchitectureType());
                summaryParams.put("complexity", summary.getComplexity());
                summaryParams.put("mainPurpose", summary.getMainPurpose());

                session.run(summaryCypher, summaryParams);
            }

            // Store individual insights
            for (InfrastructureInsight insight : insights) {
                String insightCypher = """
                    CREATE (insight:Insight {
                        type: $type,
                        title: $title,
                        description: $description,
                        severity: $severity,
                        resources: $resources,
                        createdAt: datetime()
                    })
                    """;

                Map<String, Object> insightParams = new HashMap<>();
                insightParams.put("type", insight.getType());
                insightParams.put("title", insight.getTitle());
                insightParams.put("description", insight.getDescription());
                insightParams.put("severity", insight.getSeverity());
                insightParams.put("resources", insight.getResources());

                session.run(insightCypher, insightParams);
            }
        }
    }

    /**
     * Creates indexes optimized for intelligent queries.
     */
    private void createIntelligentIndexes() {
        logger.debug("Creating intelligent indexes");

        try (Session session = driver.session()) {
            // Resource indexes
            session.run("CREATE INDEX resource_id_idx IF NOT EXISTS FOR (r:Resource) ON (r.id)");
            session.run("CREATE INDEX resource_type_idx IF NOT EXISTS FOR (r:Resource) ON (r.type)");
            session.run("CREATE INDEX resource_provider_idx IF NOT EXISTS FOR (r:Resource) ON (r.provider)");
            session.run("CREATE INDEX resource_category_idx IF NOT EXISTS FOR (r:Resource) ON (r.category)");

            // Insight indexes
            session.run("CREATE INDEX insight_type_idx IF NOT EXISTS FOR (i:Insight) ON (i.type)");
            session.run("CREATE INDEX insight_severity_idx IF NOT EXISTS FOR (i:Insight) ON (i.severity)");

            logger.debug("Intelligent indexes created successfully");
        } catch (Exception e) {
            logger.warn("Some indexes may already exist: {}", e.getMessage());
        }
    }

    /**
     * Clears the entire graph.
     */
    public void clearGraph() {
        logger.info("Clearing existing graph data");

        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
            logger.info("Graph cleared successfully");
        } catch (Exception e) {
            logger.error("Error clearing graph", e);
            throw new RuntimeException("Failed to clear Neo4j graph", e);
        }
    }

    /**
     * Gets statistics about the intelligent graph.
     */
    public Map<String, Object> getGraphStatistics() {
        try (Session session = driver.session()) {
            Map<String, Object> stats = new HashMap<>();

            // Count nodes by type
            Result nodeResult = session.run("MATCH (n) RETURN labels(n) as labels, count(n) as count");
            Map<String, Integer> nodeCounts = new HashMap<>();
            while (nodeResult.hasNext()) {
                Record record = nodeResult.next();
                List<Object> labels = record.get("labels").asList();
                int count = record.get("count").asInt();
                String labelStr = labels.isEmpty() ? "Unknown" : labels.get(0).toString();
                nodeCounts.put(labelStr, count);
            }
            stats.put("nodeCounts", nodeCounts);

            // Count relationships by type
            Result relResult = session.run("MATCH ()-[r]->() RETURN type(r) as type, count(r) as count");
            Map<String, Integer> relCounts = new HashMap<>();
            while (relResult.hasNext()) {
                Record record = relResult.next();
                String type = record.get("type").asString();
                int count = record.get("count").asInt();
                relCounts.put(type, count);
            }
            stats.put("relationshipCounts", relCounts);

            return stats;

        } catch (Exception e) {
            logger.error("Error getting graph statistics", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Sanitizes properties to avoid circular references and HCL4j object serialization issues.
     * Converts complex HCL objects to simple Java types that can be safely serialized.
     */
    private Map<String, Object> sanitizeProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> sanitized = new HashMap<>();

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            try {
                Object sanitizedValue = sanitizeValue(value);
                sanitized.put(key, sanitizedValue);
            } catch (Exception e) {
                logger.warn("Failed to sanitize property '{}' for serialization, using string representation: {}", key, e.getMessage());
                sanitized.put(key, value != null ? value.toString() : null);
            }
        }

        return sanitized;
    }

    /**
     * Recursively sanitizes a value to avoid circular references.
     */
    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }

        // Handle primitive types
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        // Handle arrays and lists
        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            java.util.List<Object> sanitizedList = new java.util.ArrayList<>();
            for (Object item : list) {
                sanitizedList.add(sanitizeValue(item));
            }
            return sanitizedList;
        }

        if (value.getClass().isArray()) {
            // Convert arrays to lists for easier handling
            Object[] array = (Object[]) value;
            java.util.List<Object> sanitizedList = new java.util.ArrayList<>();
            for (Object item : array) {
                sanitizedList.add(sanitizeValue(item));
            }
            return sanitizedList;
        }

        // Handle maps (but avoid HCL4j objects)
        if (value instanceof Map && !isHCLObject(value)) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> sanitizedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : "null";
                sanitizedMap.put(key, sanitizeValue(entry.getValue()));
            }
            return sanitizedMap;
        }

        // For HCL4j objects or other complex objects, convert to string
        if (isHCLObject(value)) {
            return extractHCLValue(value);
        }

        // For any other complex object, use toString
        return value.toString();
    }

    /**
     * Checks if an object is an HCL4j object that might have circular references.
     */
    private boolean isHCLObject(Object value) {
        if (value == null) {
            return false;
        }

        String className = value.getClass().getName();
        return className.startsWith("com.bertramlabs.plugins.hcl4j");
    }

    /**
     * Extracts the actual value from HCL4j objects safely.
     */
    private Object extractHCLValue(Object hclObject) {
        try {
            // Try to get the value using reflection to avoid circular references
            if (hclObject.getClass().getSimpleName().equals("HCLValue")) {
                // For HCLValue objects, try to get the actual value
                java.lang.reflect.Method getValueMethod = hclObject.getClass().getMethod("getValue");
                Object actualValue = getValueMethod.invoke(hclObject);

                // Recursively sanitize the extracted value
                return sanitizeValue(actualValue);
            } else if (hclObject.getClass().getSimpleName().equals("HCLArray")) {
                // For HCLArray objects, try to get the children as a simple list
                java.lang.reflect.Method getChildrenMethod = hclObject.getClass().getMethod("getChildren");
                Object children = getChildrenMethod.invoke(hclObject);

                if (children instanceof java.util.List) {
                    java.util.List<?> childList = (java.util.List<?>) children;
                    java.util.List<Object> sanitizedChildren = new java.util.ArrayList<>();

                    for (Object child : childList) {
                        sanitizedChildren.add(extractHCLValue(child));
                    }

                    return sanitizedChildren;
                }
            } else if (hclObject.getClass().getSimpleName().equals("VariableTree")) {
                // For HCL variables/references, extract the reference name
                try {
                    java.lang.reflect.Method getNameMethod = hclObject.getClass().getMethod("getName");
                    Object name = getNameMethod.invoke(hclObject);
                    if (name != null) {
                        return name.toString();
                    }
                } catch (Exception e) {
                    logger.debug("Failed to extract name from VariableTree: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract HCL value using reflection: {}", e.getMessage());
        }

        // Fallback to string representation
        return hclObject.toString();
    }

    // Helper methods
    private String extractResourceName(String fullIdentifier) {
        // Extract resource name from "resource_type.resource_name" format
        if (fullIdentifier != null && fullIdentifier.contains(".")) {
            return fullIdentifier.split("\\.", 2)[1];
        }
        return fullIdentifier;
    }

    private String sanitizeRelationshipType(String type) {
        // Neo4j relationship types cannot contain spaces or special characters
        return type.replaceAll("[^A-Z_]", "_");
    }

    private String categorizeResource(String resourceType) {
        if (resourceType == null) return "unknown";

        String lowerType = resourceType.toLowerCase();

        if (lowerType.contains("instance") || lowerType.contains("vm") ||
                lowerType.contains("compute") || lowerType.contains("lambda")) {
            return "compute";
        } else if (lowerType.contains("bucket") || lowerType.contains("disk") ||
                lowerType.contains("volume")) {
            return "storage";
        } else if (lowerType.contains("db") || lowerType.contains("database") ||
                lowerType.contains("sql")) {
            return "database";
        } else if (lowerType.contains("vpc") || lowerType.contains("subnet") ||
                lowerType.contains("gateway") || lowerType.contains("lb")) {
            return "network";
        } else if (lowerType.contains("iam") || lowerType.contains("role") ||
                lowerType.contains("policy")) {
            return "identity";
        }

        return "other";
    }
}