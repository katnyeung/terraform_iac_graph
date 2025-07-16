package com.terraform.neo4j.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraform.neo4j.model.ParsedTerraform;
import com.terraform.neo4j.model.TerraformResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that summarizes parsed Terraform configuration into a structured JSON format
 * suitable for LLM analysis.
 */
@Service
public class TerraformSummarizer {

    private static final Logger logger = LoggerFactory.getLogger(TerraformSummarizer.class);
    private final ObjectMapper objectMapper;

    public TerraformSummarizer() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a comprehensive JSON summary of the parsed Terraform configuration
     * for LLM analysis.
     *
     * @param parsed The parsed Terraform configuration
     * @return JSON string containing structured summary
     */
    public String createSummary(ParsedTerraform parsed) {
        logger.info("Creating Terraform summary for LLM analysis");

        try {
            // Create a clean, simplified summary focused on what matters for LLM analysis
            Map<String, Object> summary = new HashMap<>();

            // Basic metadata
            summary.put("resourceCount", parsed.getResourceCount());

            // Resource type counts for quick overview
            Map<String, Long> resourceTypeCounts = parsed.getResources().stream()
                    .collect(Collectors.groupingBy(TerraformResource::getType, Collectors.counting()));
            summary.put("resourceTypeCounts", resourceTypeCounts);

            // Group resources by provider for better context
            Map<String, List<Map<String, Object>>> resourcesByProvider = new HashMap<>();

            for (TerraformResource resource : parsed.getResources()) {
                String provider = detectProviderFromType(resource.getType());

                Map<String, Object> simplifiedResource = new HashMap<>();
                simplifiedResource.put("id", resource.getType() + "." + resource.getName());
                simplifiedResource.put("type", resource.getType());
                simplifiedResource.put("name", resource.getName());

                // Extract only the important properties, avoiding circular references
                Map<String, Object> cleanProperties = extractImportantProperties(resource);
                simplifiedResource.put("properties", cleanProperties);

                // Add category for better context
                simplifiedResource.put("category", categorizeResource(resource.getType()));

                // Add to provider group
                resourcesByProvider
                        .computeIfAbsent(provider, k -> new ArrayList<>())
                        .add(simplifiedResource);
            }

            summary.put("resourcesByProvider", resourcesByProvider);

            // Add logical component groupings for better LLM understanding
            Map<String, Object> logicalComponents = createLogicalComponentGroups(parsed);
            summary.put("logicalComponents", logicalComponents);

            // Add architectural context
            Map<String, Object> architecturalContext = createArchitecturalContext(parsed);
            summary.put("architecturalContext", architecturalContext);

            // Add variables for context (simplified)
            if (parsed.getVariables() != null && !parsed.getVariables().isEmpty()) {
                List<Map<String, Object>> variables = new ArrayList<>();
                parsed.getVariables().forEach((name, variable) -> {
                    Map<String, Object> var = new HashMap<>();
                    var.put("name", name);

                    // Handle variable as a Map or Object
                    if (variable instanceof Map) {
                        Map<?, ?> varMap = (Map<?, ?>) variable;
                        var.put("description", extractSimpleValue(varMap.get("description")));
                        var.put("defaultValue", extractSimpleValue(varMap.get("default")));
                        var.put("type", extractSimpleValue(varMap.get("type")));
                    } else {
                        var.put("value", extractSimpleValue(variable));
                    }

                    variables.add(var);
                });
                summary.put("variables", variables);
            }

            // Add outputs for context (simplified)
            if (parsed.getOutputs() != null && !parsed.getOutputs().isEmpty()) {
                List<Map<String, Object>> outputs = new ArrayList<>();
                parsed.getOutputs().forEach((name, output) -> {
                    Map<String, Object> out = new HashMap<>();
                    out.put("name", name);

                    // Handle output as a Map or Object
                    if (output instanceof Map) {
                        Map<?, ?> outMap = (Map<?, ?>) output;
                        out.put("description", extractSimpleValue(outMap.get("description")));
                        out.put("value", extractSimpleValue(outMap.get("value")));
                        out.put("sensitive", extractSimpleValue(outMap.get("sensitive")));
                    } else {
                        out.put("value", extractSimpleValue(output));
                    }

                    outputs.add(out);
                });
                summary.put("outputs", outputs);
            }

            // Add providers configuration (simplified)
            if (parsed.getProviders() != null && !parsed.getProviders().isEmpty()) {
                Map<String, Object> providers = new HashMap<>();
                parsed.getProviders().forEach((name, config) -> {
                    Map<String, Object> providerConfig = new HashMap<>();
                    if (config != null) {
                        // Handle config as a Map or Object
                        if (config instanceof Map) {
                            Map<?, ?> configMap = (Map<?, ?>) config;
                            configMap.forEach((key, value) -> {
                                providerConfig.put(key.toString(), extractSimpleValue(value));
                            });
                        } else {
                            // If it's not a Map, just store the string representation
                            providerConfig.put("value", extractSimpleValue(config));
                        }
                    }
                    providers.put(name, providerConfig);
                });
                summary.put("providers", providers);
            }

            // Convert to JSON string
            String jsonSummary = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);

            logger.info("Created Terraform summary with {} resources for LLM analysis", parsed.getResourceCount());
            logger.debug("Summary JSON length: {} characters", jsonSummary.length());
            logger.debug("Summary content: {}", jsonSummary);

            return jsonSummary;

        } catch (Exception e) {
            logger.error("Error creating Terraform summary", e);
            throw new RuntimeException("Failed to create Terraform summary for LLM analysis", e);
        }
    }

    /**
     * Creates a detailed summary of a single Terraform resource.
     *
     * @param resource The Terraform resource to summarize
     * @return Map containing resource details
     */
    private Map<String, Object> summarizeResource(TerraformResource resource) {
        Map<String, Object> resourceSummary = new HashMap<>();

        resourceSummary.put("type", resource.getType());
        resourceSummary.put("name", resource.getName());
        resourceSummary.put("fullIdentifier", resource.getType() + "." + resource.getName());

        // Safely serialize properties to avoid circular references
        resourceSummary.put("properties", sanitizeProperties(resource.getArguments()));

        // Add metadata for LLM context
        resourceSummary.put("providerHint", detectProviderFromType(resource.getType()));
        resourceSummary.put("categoryHint", categorizeResource(resource.getType()));

        return resourceSummary;
    }

    /**
     * Sanitizes properties to avoid circular references and HCL4j object serialization issues.
     * Converts complex HCL objects to simple Java types that can be safely serialized.
     *
     * @param properties Original properties map
     * @return Sanitized properties map
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
                logger.warn("Failed to sanitize property '{}', using string representation: {}", key, e.getMessage());
                sanitized.put(key, value != null ? value.toString() : null);
            }
        }

        return sanitized;
    }

    /**
     * Recursively sanitizes a value to avoid circular references.
     *
     * @param value Value to sanitize
     * @return Sanitized value
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
            java.util.List<Object> sanitizedList = new ArrayList<>();
            for (Object item : list) {
                sanitizedList.add(sanitizeValue(item));
            }
            return sanitizedList;
        }

        if (value.getClass().isArray()) {
            // Convert arrays to lists for easier handling
            Object[] array = (Object[]) value;
            java.util.List<Object> sanitizedList = new ArrayList<>();
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
     *
     * @param value Object to check
     * @return true if it's an HCL4j object
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
     *
     * @param hclObject HCL4j object
     * @return Extracted value
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
                    java.util.List<Object> sanitizedChildren = new ArrayList<>();

                    for (Object child : childList) {
                        sanitizedChildren.add(extractHCLValue(child));
                    }

                    return sanitizedChildren;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract HCL value using reflection: {}", e.getMessage());
        }

        // Fallback to string representation
        return hclObject.toString();
    }

    /**
     * Extracts important properties from a resource while avoiding circular references.
     * Focuses on properties that are most relevant for understanding the resource.
     */
    private Map<String, Object> extractImportantProperties(TerraformResource resource) {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> arguments = resource.getArguments();

        if (arguments == null) {
            return properties;
        }

        // Extract common important properties based on resource type
        String resourceType = resource.getType();

        // For all resources, try to extract name, id, tags if present
        extractIfPresent(arguments, properties, "name");
        extractIfPresent(arguments, properties, "id");
        extractIfPresent(arguments, properties, "tags");

        // Resource-specific important properties
        if (resourceType.contains("instance") || resourceType.contains("vm")) {
            // For compute instances
            extractIfPresent(arguments, properties, "instance_type");
            extractIfPresent(arguments, properties, "machine_type");
            extractIfPresent(arguments, properties, "ami");
            extractIfPresent(arguments, properties, "image");
            extractIfPresent(arguments, properties, "size");
        } else if (resourceType.contains("security_group")) {
            // For security groups
            extractIfPresent(arguments, properties, "vpc_id");
            extractIfPresent(arguments, properties, "description");
        } else if (resourceType.contains("helm_release")) {
            // For Helm releases
            extractIfPresent(arguments, properties, "chart");
            extractIfPresent(arguments, properties, "repository");
            extractIfPresent(arguments, properties, "namespace");
            extractIfPresent(arguments, properties, "version");
            extractIfPresent(arguments, properties, "create_namespace");

            // Handle set values specially
            if (arguments.containsKey("set")) {
                Object setValue = arguments.get("set");
                if (setValue instanceof List) {
                    List<?> setList = (List<?>) setValue;
                    List<Map<String, String>> cleanSetList = new ArrayList<>();

                    for (Object setItem : setList) {
                        if (setItem instanceof Map) {
                            Map<?, ?> setMap = (Map<?, ?>) setItem;
                            Map<String, String> cleanSet = new HashMap<>();

                            if (setMap.containsKey("name")) {
                                cleanSet.put("name", String.valueOf(setMap.get("name")));
                            }
                            if (setMap.containsKey("value")) {
                                cleanSet.put("value", String.valueOf(setMap.get("value")));
                            }

                            cleanSetList.add(cleanSet);
                        }
                    }

                    properties.put("set", cleanSetList);
                }
            }

            // Handle depends_on specially
            extractDependsOn(arguments, properties);
        } else if (resourceType.contains("storage") || resourceType.contains("bucket")) {
            // For storage resources
            extractIfPresent(arguments, properties, "bucket");
            extractIfPresent(arguments, properties, "acl");
            extractIfPresent(arguments, properties, "storage_class");
        } else if (resourceType.contains("database") || resourceType.contains("db")) {
            // For database resources
            extractIfPresent(arguments, properties, "engine");
            extractIfPresent(arguments, properties, "version");
            extractIfPresent(arguments, properties, "instance_class");
            extractIfPresent(arguments, properties, "storage_type");
        } else if (resourceType.contains("vpc") || resourceType.contains("network")) {
            // For network resources
            extractIfPresent(arguments, properties, "cidr_block");
            extractIfPresent(arguments, properties, "address_space");
        } else if (resourceType.contains("iam") || resourceType.contains("role") || resourceType.contains("policy")) {
            // For IAM resources
            extractIfPresent(arguments, properties, "role");
            extractIfPresent(arguments, properties, "policy");
            extractIfPresent(arguments, properties, "actions");
        } else if (resourceType.contains("kubernetes")) {
            // For Kubernetes resources
            extractIfPresent(arguments, properties, "metadata");
            extractIfPresent(arguments, properties, "spec");
        } else if (resourceType.contains("file_system") || resourceType.contains("mount")) {
            // For file systems and mounts
            extractIfPresent(arguments, properties, "file_system_id");
            extractIfPresent(arguments, properties, "subnet_id");
            extractIfPresent(arguments, properties, "security_groups");
        }

        return properties;
    }

    /**
     * Safely extracts a property if it exists in the source map.
     */
    private void extractIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            Object value = source.get(key);
            if (value != null) {
                target.put(key, extractSimpleValue(value));
            }
        }
    }

    /**
     * Extracts depends_on relationships which are important for understanding resource dependencies.
     */
    private void extractDependsOn(Map<String, Object> source, Map<String, Object> target) {
        if (source.containsKey("depends_on")) {
            Object dependsOn = source.get("depends_on");
            if (dependsOn instanceof List) {
                List<?> dependsList = (List<?>) dependsOn;
                List<String> cleanDepends = new ArrayList<>();

                for (Object depend : dependsList) {
                    cleanDepends.add(String.valueOf(depend));
                }

                target.put("depends_on", cleanDepends);
            } else if (dependsOn != null) {
                target.put("depends_on", dependsOn.toString());
            }
        }
    }

    /**
     * Extracts simple values from complex HCL objects, avoiding circular references.
     * This method converts HCL4j objects to simple strings or basic types.
     */
    private Object extractSimpleValue(Object value) {
        if (value == null) {
            return null;
        }

        // Handle primitive types
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        // Handle HCL4j objects by extracting their string representation
        if (isHCLObject(value)) {
            try {
                // For HCL variables/references, extract the reference name
                if (value.getClass().getSimpleName().equals("VariableTree")) {
                    // Try to get the name or string representation
                    java.lang.reflect.Method getNameMethod = value.getClass().getMethod("getName");
                    Object name = getNameMethod.invoke(value);
                    if (name != null) {
                        return name.toString();
                    }
                }

                // For other HCL objects, use toString as fallback
                return value.toString();
            } catch (Exception e) {
                logger.debug("Failed to extract simple value from HCL object: {}", e.getMessage());
                return value.toString();
            }
        }

        // Handle simple lists
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> simpleList = new ArrayList<>();
            for (Object item : list) {
                simpleList.add(extractSimpleValue(item));
            }
            return simpleList;
        }

        // Handle simple maps
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> simpleMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : "null";
                simpleMap.put(key, extractSimpleValue(entry.getValue()));
            }
            return simpleMap;
        }

        // For any other complex object, use toString
        return value.toString();
    }

    /**
     * Creates logical component groups to help LLM understand the architecture better.
     * Groups related resources together (e.g., EKS cluster with its components).
     */
    private Map<String, Object> createLogicalComponentGroups(ParsedTerraform parsed) {
        Map<String, Object> logicalComponents = new HashMap<>();

        // Extract cluster name from variables for grouping
        String clusterName = extractClusterName(parsed);

        // Group EKS-related components
        Map<String, Object> eksCluster = createEKSClusterGroup(parsed, clusterName);
        if (!eksCluster.isEmpty()) {
            logicalComponents.put("EKS_CLUSTER_" + clusterName.toUpperCase(), eksCluster);
        }

        // Group EFS-related components
        Map<String, Object> efsStorage = createEFSStorageGroup(parsed);
        if (!efsStorage.isEmpty()) {
            logicalComponents.put("EFS_STORAGE", efsStorage);
        }

        // Group Helm applications
        Map<String, Object> helmApps = createHelmApplicationsGroup(parsed);
        if (!helmApps.isEmpty()) {
            logicalComponents.put("HELM_APPLICATIONS", helmApps);
        }

        // Group Security components
        Map<String, Object> securityComponents = createSecurityGroup(parsed);
        if (!securityComponents.isEmpty()) {
            logicalComponents.put("SECURITY_COMPONENTS", securityComponents);
        }

        return logicalComponents;
    }

    /**
     * Creates architectural context to help LLM understand the overall system design.
     */
    private Map<String, Object> createArchitecturalContext(ParsedTerraform parsed) {
        Map<String, Object> context = new HashMap<>();

        // Identify the main architecture pattern
        String architectureType = identifyArchitectureType(parsed);
        context.put("architectureType", architectureType);

        // Identify key relationships and dependencies
        List<Map<String, String>> keyRelationships = identifyKeyRelationships(parsed);
        context.put("keyRelationships", keyRelationships);

        // Identify the central components
        List<String> centralComponents = identifyCentralComponents(parsed);
        context.put("centralComponents", centralComponents);

        // Add deployment context
        Map<String, Object> deploymentContext = createDeploymentContext(parsed);
        context.put("deploymentContext", deploymentContext);

        return context;
    }

    /**
     * Extracts cluster name from variables or defaults to a standard name.
     */
    private String extractClusterName(ParsedTerraform parsed) {
        if (parsed.getVariables() != null) {
            Object clusterNameVar = parsed.getVariables().get("cluster_name");
            if (clusterNameVar instanceof Map) {
                Map<?, ?> varMap = (Map<?, ?>) clusterNameVar;
                Object defaultValue = varMap.get("default");
                if (defaultValue != null) {
                    return extractSimpleValue(defaultValue).toString();
                }
            }
        }
        return "ai_impact"; // Default based on your example
    }

    /**
     * Creates EKS cluster logical group with all related components.
     */
    private Map<String, Object> createEKSClusterGroup(ParsedTerraform parsed, String clusterName) {
        Map<String, Object> eksGroup = new HashMap<>();

        // Add cluster metadata
        eksGroup.put("name", clusterName);
        eksGroup.put("type", "EKS_CLUSTER");
        eksGroup.put("description", "Amazon EKS cluster with associated resources");

        // Find EKS-related outputs
        List<Map<String, Object>> eksOutputs = new ArrayList<>();
        if (parsed.getOutputs() != null) {
            parsed.getOutputs().forEach((name, output) -> {
                if (name.contains("cluster") || name.contains("eks") || name.contains("node")) {
                    Map<String, Object> outputInfo = new HashMap<>();
                    outputInfo.put("name", name);
                    if (output instanceof Map) {
                        Map<?, ?> outMap = (Map<?, ?>) output;
                        outputInfo.put("description", extractSimpleValue(outMap.get("description")));
                        outputInfo.put("value", extractSimpleValue(outMap.get("value")));
                    }
                    eksOutputs.add(outputInfo);
                }
            });
        }
        eksGroup.put("outputs", eksOutputs);

        // Find related Helm applications that depend on EKS
        List<String> dependentHelmApps = new ArrayList<>();
        for (TerraformResource resource : parsed.getResources()) {
            if ("helm_release".equals(resource.getType())) {
                dependentHelmApps.add(resource.getType() + "." + resource.getName());
            }
        }
        eksGroup.put("dependentApplications", dependentHelmApps);

        return eksGroup;
    }

    /**
     * Creates EFS storage logical group.
     */
    private Map<String, Object> createEFSStorageGroup(ParsedTerraform parsed) {
        Map<String, Object> efsGroup = new HashMap<>();

        List<Map<String, Object>> efsComponents = new ArrayList<>();

        for (TerraformResource resource : parsed.getResources()) {
            if (resource.getType().contains("efs")) {
                Map<String, Object> component = new HashMap<>();
                component.put("id", resource.getType() + "." + resource.getName());
                component.put("type", resource.getType());
                component.put("name", resource.getName());
                component.put("properties", extractImportantProperties(resource));
                efsComponents.add(component);
            }
        }

        if (!efsComponents.isEmpty()) {
            efsGroup.put("type", "EFS_STORAGE_SYSTEM");
            efsGroup.put("description", "Amazon EFS file system with mount targets");
            efsGroup.put("components", efsComponents);

            // Add storage class if present
            for (TerraformResource resource : parsed.getResources()) {
                if ("kubernetes_storage_class".equals(resource.getType()) && "efs".equals(resource.getName())) {
                    Map<String, Object> storageClass = new HashMap<>();
                    storageClass.put("id", resource.getType() + "." + resource.getName());
                    storageClass.put("type", resource.getType());
                    storageClass.put("name", resource.getName());
                    efsGroup.put("kubernetesStorageClass", storageClass);
                    break;
                }
            }
        }

        return efsGroup;
    }

    /**
     * Creates Helm applications logical group.
     */
    private Map<String, Object> createHelmApplicationsGroup(ParsedTerraform parsed) {
        Map<String, Object> helmGroup = new HashMap<>();

        List<Map<String, Object>> helmApps = new ArrayList<>();

        for (TerraformResource resource : parsed.getResources()) {
            if ("helm_release".equals(resource.getType())) {
                Map<String, Object> app = new HashMap<>();
                app.put("id", resource.getType() + "." + resource.getName());
                app.put("name", resource.getName());
                app.put("properties", extractImportantProperties(resource));

                // Categorize the application
                String appCategory = categorizeHelmApp(resource.getName());
                app.put("category", appCategory);

                helmApps.add(app);
            }
        }

        if (!helmApps.isEmpty()) {
            helmGroup.put("type", "HELM_APPLICATIONS");
            helmGroup.put("description", "Kubernetes applications deployed via Helm");
            helmGroup.put("applications", helmApps);
        }

        return helmGroup;
    }

    /**
     * Creates security components logical group.
     */
    private Map<String, Object> createSecurityGroup(ParsedTerraform parsed) {
        Map<String, Object> securityGroup = new HashMap<>();

        List<Map<String, Object>> securityComponents = new ArrayList<>();

        for (TerraformResource resource : parsed.getResources()) {
            if (resource.getType().contains("security_group") ||
                    resource.getType().contains("ingress_rule") ||
                    resource.getType().contains("iam") ||
                    resource.getType().contains("policy")) {

                Map<String, Object> component = new HashMap<>();
                component.put("id", resource.getType() + "." + resource.getName());
                component.put("type", resource.getType());
                component.put("name", resource.getName());
                component.put("properties", extractImportantProperties(resource));
                securityComponents.add(component);
            }
        }

        if (!securityComponents.isEmpty()) {
            securityGroup.put("type", "SECURITY_COMPONENTS");
            securityGroup.put("description", "Security groups, rules, and access policies");
            securityGroup.put("components", securityComponents);
        }

        return securityGroup;
    }

    /**
     * Identifies the overall architecture type.
     */
    private String identifyArchitectureType(ParsedTerraform parsed) {
        boolean hasEKS = parsed.getOutputs() != null &&
                parsed.getOutputs().keySet().stream().anyMatch(key -> key.contains("cluster"));
        boolean hasHelm = parsed.getResources().stream().anyMatch(r -> "helm_release".equals(r.getType()));
        boolean hasEFS = parsed.getResources().stream().anyMatch(r -> r.getType().contains("efs"));

        if (hasEKS && hasHelm && hasEFS) {
            return "KUBERNETES_MICROSERVICES_WITH_SHARED_STORAGE";
        } else if (hasEKS && hasHelm) {
            return "KUBERNETES_MICROSERVICES";
        } else if (hasEKS) {
            return "CONTAINER_ORCHESTRATION";
        } else {
            return "CLOUD_INFRASTRUCTURE";
        }
    }

    /**
     * Identifies key relationships between components.
     */
    private List<Map<String, String>> identifyKeyRelationships(ParsedTerraform parsed) {
        List<Map<String, String>> relationships = new ArrayList<>();

        // EKS to Helm relationships
        for (TerraformResource resource : parsed.getResources()) {
            if ("helm_release".equals(resource.getType())) {
                Map<String, String> rel = new HashMap<>();
                rel.put("source", resource.getType() + "." + resource.getName());
                rel.put("target", "module.eks");
                rel.put("type", "DEPLOYED_ON");
                rel.put("description", "Helm application deployed on EKS cluster");
                relationships.add(rel);
            }
        }

        // EFS to EKS relationship
        boolean hasEFS = parsed.getResources().stream().anyMatch(r -> r.getType().contains("efs"));
        if (hasEFS) {
            Map<String, String> rel = new HashMap<>();
            rel.put("source", "aws_efs_file_system.ai_impact");
            rel.put("target", "module.eks");
            rel.put("type", "PROVIDES_STORAGE_FOR");
            rel.put("description", "EFS provides persistent storage for EKS workloads");
            relationships.add(rel);
        }

        // Security group relationships
        for (TerraformResource resource : parsed.getResources()) {
            if (resource.getType().contains("efs_mount_target")) {
                Map<String, String> rel = new HashMap<>();
                rel.put("source", resource.getType() + "." + resource.getName());
                rel.put("target", "aws_security_group.allow_nfs");
                rel.put("type", "PROTECTED_BY");
                rel.put("description", "EFS mount target protected by NFS security group");
                relationships.add(rel);
            }
        }

        return relationships;
    }

    /**
     * Identifies central components in the architecture.
     */
    private List<String> identifyCentralComponents(ParsedTerraform parsed) {
        List<String> centralComponents = new ArrayList<>();

        // EKS cluster is typically central
        if (parsed.getOutputs() != null &&
                parsed.getOutputs().keySet().stream().anyMatch(key -> key.contains("cluster"))) {
            centralComponents.add("module.eks");
        }

        // EFS if it's shared storage
        boolean hasEFS = parsed.getResources().stream().anyMatch(r -> r.getType().contains("efs"));
        if (hasEFS) {
            centralComponents.add("aws_efs_file_system.ai_impact");
        }

        return centralComponents;
    }

    /**
     * Creates deployment context information.
     */
    private Map<String, Object> createDeploymentContext(ParsedTerraform parsed) {
        Map<String, Object> context = new HashMap<>();

        // Extract environment from variables
        if (parsed.getVariables() != null) {
            Object envVar = parsed.getVariables().get("environment");
            if (envVar instanceof Map) {
                Map<?, ?> varMap = (Map<?, ?>) envVar;
                Object defaultValue = varMap.get("default");
                if (defaultValue != null) {
                    context.put("environment", extractSimpleValue(defaultValue).toString());
                }
            }
        }

        // Extract region from providers
        if (parsed.getProviders() != null) {
            Object awsProvider = parsed.getProviders().get("aws");
            if (awsProvider instanceof Map) {
                Map<?, ?> providerMap = (Map<?, ?>) awsProvider;
                Object region = providerMap.get("region");
                if (region != null) {
                    context.put("region", extractSimpleValue(region).toString());
                }
            }
        }

        // Count resources by category
        Map<String, Long> resourcesByCategory = parsed.getResources().stream()
                .collect(Collectors.groupingBy(r -> categorizeResource(r.getType()), Collectors.counting()));
        context.put("resourcesByCategory", resourcesByCategory);

        return context;
    }

    /**
     * Categorizes Helm applications.
     */
    private String categorizeHelmApp(String appName) {
        String lowerName = appName.toLowerCase();

        if (lowerName.contains("cert") || lowerName.contains("tls") || lowerName.contains("ssl")) {
            return "CERTIFICATE_MANAGEMENT";
        } else if (lowerName.contains("ingress") || lowerName.contains("nginx") || lowerName.contains("traefik")) {
            return "INGRESS_CONTROLLER";
        } else if (lowerName.contains("monitor") || lowerName.contains("prometheus") || lowerName.contains("grafana")) {
            return "MONITORING";
        } else if (lowerName.contains("log") || lowerName.contains("elastic") || lowerName.contains("fluentd")) {
            return "LOGGING";
        } else {
            return "APPLICATION";
        }
    }

    /**
     * Detects the cloud provider from resource type.
     */
    private String detectProviderFromType(String resourceType) {
        if (resourceType == null) return "unknown";

        String lowerType = resourceType.toLowerCase();
        if (lowerType.startsWith("aws_")) return "aws";
        if (lowerType.startsWith("google_") || lowerType.startsWith("gcp_")) return "gcp";
        if (lowerType.startsWith("azurerm_") || lowerType.startsWith("azure_")) return "azure";
        if (lowerType.startsWith("kubernetes_") || lowerType.startsWith("k8s_")) return "kubernetes";
        if (lowerType.startsWith("helm_")) return "helm";
        if (lowerType.startsWith("docker_")) return "docker";

        return "unknown";
    }

    /**
     * Categorizes resource into broad categories for LLM context.
     */
    private String categorizeResource(String resourceType) {
        if (resourceType == null) return "unknown";

        String lowerType = resourceType.toLowerCase();

        // Compute resources
        if (lowerType.contains("instance") || lowerType.contains("vm") ||
                lowerType.contains("compute") || lowerType.contains("ecs") ||
                lowerType.contains("lambda") || lowerType.contains("function")) {
            return "compute";
        }

        // Storage resources
        if (lowerType.contains("bucket") || lowerType.contains("disk") ||
                lowerType.contains("volume") || lowerType.contains("storage")) {
            return "storage";
        }

        // Database resources
        if (lowerType.contains("db") || lowerType.contains("database") ||
                lowerType.contains("sql") || lowerType.contains("redis") ||
                lowerType.contains("mongo")) {
            return "database";
        }

        // Network resources
        if (lowerType.contains("vpc") || lowerType.contains("subnet") ||
                lowerType.contains("gateway") || lowerType.contains("lb") ||
                lowerType.contains("loadbalancer") || lowerType.contains("security_group")) {
            return "network";
        }

        // Identity and access
        if (lowerType.contains("iam") || lowerType.contains("role") ||
                lowerType.contains("policy") || lowerType.contains("user") ||
                lowerType.contains("service_account")) {
            return "identity";
        }

        // Container orchestration
        if (lowerType.contains("helm") || lowerType.contains("kubernetes") ||
                lowerType.contains("k8s") || lowerType.contains("deployment") ||
                lowerType.contains("service")) {
            return "orchestration";
        }

        return "other";
    }
}