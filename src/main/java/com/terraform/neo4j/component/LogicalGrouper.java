package com.terraform.neo4j.component;

import com.terraform.neo4j.model.DependencyMap;
import com.terraform.neo4j.model.FileStructure;
import com.terraform.neo4j.model.LogicalGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Component responsible for grouping related Terraform resources logically.
 * Groups resources by provider, functionality, and relationships.
 */
@Component
public class LogicalGrouper {
    
    private static final Logger logger = LoggerFactory.getLogger(LogicalGrouper.class);
    
    // Resource type patterns for logical grouping
    private static final Map<String, String[]> RESOURCE_CATEGORIES = Map.of(
            "Compute", new String[]{"aws_instance", "aws_launch_template", "aws_autoscaling_group", "aws_ecs_service", "aws_ecs_task_definition", "aws_lambda_function"},
            "Storage", new String[]{"aws_s3_bucket", "aws_ebs_volume", "aws_efs_file_system", "aws_fsx_file_system"},
            "Network", new String[]{"aws_vpc", "aws_subnet", "aws_internet_gateway", "aws_nat_gateway", "aws_route_table", "aws_security_group"},
            "Database", new String[]{"aws_db_instance", "aws_rds_cluster", "aws_dynamodb_table", "aws_elasticache_cluster"},
            "Container", new String[]{"aws_eks_cluster", "aws_eks_node_group", "aws_ecs_cluster", "helm_release", "kubernetes_deployment"},
            "Security", new String[]{"aws_iam_role", "aws_iam_policy", "aws_kms_key", "aws_acm_certificate"},
            "Monitoring", new String[]{"aws_cloudwatch_log_group", "aws_cloudwatch_metric_alarm", "aws_sns_topic"},
            "Load_Balancing", new String[]{"aws_lb", "aws_alb", "aws_elb", "aws_lb_target_group", "aws_lb_listener"}
    );
    
    // Provider-based grouping patterns
    private static final Map<String, String> PROVIDER_GROUPS = Map.of(
            "aws", "AWS Infrastructure",
            "kubernetes", "Kubernetes Resources",
            "helm", "Helm Applications",
            "azurerm", "Azure Resources",
            "google", "Google Cloud Resources",
            "local", "Local Resources",
            "random", "Random Resources",
            "tls", "TLS Resources"
    );

    /**
     * Group resources logically based on structure and dependencies
     */
    public LogicalGroups groupResources(FileStructure structure, DependencyMap dependencies) {
        logger.info("Starting logical grouping of {} total resources", structure.getTotalResources());
        
        Map<String, List<String>> groupedResources = new LinkedHashMap<>();
        Map<String, String> groupDescriptions = new HashMap<>();
        List<String> groupOrder = new ArrayList<>();
        
        // Get all resources from the structure
        Set<String> allResources = getAllResources(structure);
        
        // Group by provider first
        Map<String, List<String>> providerGroups = groupByProvider(allResources, structure.getResourceTypes());
        
        // Within each provider, group by functionality
        for (Map.Entry<String, List<String>> providerEntry : providerGroups.entrySet()) {
            String provider = providerEntry.getKey();
            List<String> providerResources = providerEntry.getValue();
            
            if (providerResources.isEmpty()) {
                continue;
            }
            
            // Group resources within this provider by category
            Map<String, List<String>> categoryGroups = groupByCategory(providerResources, structure.getResourceTypes());
            
            // Create logical groups for this provider
            for (Map.Entry<String, List<String>> categoryEntry : categoryGroups.entrySet()) {
                String category = categoryEntry.getKey();
                List<String> categoryResources = categoryEntry.getValue();
                
                if (categoryResources.isEmpty()) {
                    continue;
                }
                
                String groupName = createGroupName(provider, category);
                String description = createGroupDescription(provider, category, categoryResources.size());
                
                groupedResources.put(groupName, new ArrayList<>(categoryResources));
                groupDescriptions.put(groupName, description);
                groupOrder.add(groupName);
                
                logger.debug("Created group '{}' with {} resources: {}", 
                    groupName, categoryResources.size(), description);
            }
        }
        
        // Group any remaining ungrouped resources
        Set<String> groupedResourceSet = groupedResources.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        
        List<String> ungroupedResources = allResources.stream()
                .filter(resource -> !groupedResourceSet.contains(resource))
                .collect(Collectors.toList());
        
        if (!ungroupedResources.isEmpty()) {
            String miscGroupName = "Miscellaneous";
            groupedResources.put(miscGroupName, ungroupedResources);
            groupDescriptions.put(miscGroupName, 
                String.format("Miscellaneous resources that don't fit into other categories (%d resources)", 
                    ungroupedResources.size()));
            groupOrder.add(miscGroupName);
            
            logger.debug("Created miscellaneous group with {} ungrouped resources", ungroupedResources.size());
        }
        
        // Optimize group order based on dependencies
        List<String> optimizedOrder = optimizeGroupOrder(groupOrder, groupedResources, dependencies);
        
        LogicalGroups logicalGroups = new LogicalGroups(groupedResources, groupDescriptions, optimizedOrder);
        
        logger.info("Logical grouping completed. Created {} groups with {} total resources", 
            logicalGroups.getTotalGroups(), logicalGroups.getTotalResources());
        
        return logicalGroups;
    }
    
    /**
     * Get all resources from the file structure
     */
    private Set<String> getAllResources(FileStructure structure) {
        Set<String> allResources = new HashSet<>();
        
        if (structure.getResourcesByFile() != null) {
            for (List<String> resources : structure.getResourcesByFile().values()) {
                allResources.addAll(resources);
            }
        }
        
        return allResources;
    }
    
    /**
     * Group resources by provider
     */
    private Map<String, List<String>> groupByProvider(Set<String> resources, Map<String, String> resourceTypes) {
        Map<String, List<String>> providerGroups = new LinkedHashMap<>();
        
        for (String resource : resources) {
            String resourceType = resourceTypes.get(resource);
            if (resourceType == null) {
                continue;
            }
            
            String provider = extractProvider(resourceType);
            providerGroups.computeIfAbsent(provider, k -> new ArrayList<>()).add(resource);
        }
        
        // Sort providers by priority (AWS first, then alphabetically)
        return providerGroups.entrySet().stream()
                .sorted((e1, e2) -> {
                    String p1 = e1.getKey();
                    String p2 = e2.getKey();
                    
                    if ("aws".equals(p1) && !"aws".equals(p2)) return -1;
                    if ("aws".equals(p2) && !"aws".equals(p1)) return 1;
                    if ("kubernetes".equals(p1) && !"kubernetes".equals(p2) && !"aws".equals(p2)) return -1;
                    if ("kubernetes".equals(p2) && !"kubernetes".equals(p1) && !"aws".equals(p1)) return 1;
                    
                    return p1.compareTo(p2);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
    
    /**
     * Extract provider from resource type
     */
    private String extractProvider(String resourceType) {
        if (resourceType == null || !resourceType.contains("_")) {
            return "unknown";
        }
        
        return resourceType.split("_")[0];
    }
    
    /**
     * Group resources by category within a provider
     */
    private Map<String, List<String>> groupByCategory(List<String> resources, Map<String, String> resourceTypes) {
        Map<String, List<String>> categoryGroups = new LinkedHashMap<>();
        
        for (String resource : resources) {
            String resourceType = resourceTypes.get(resource);
            if (resourceType == null) {
                continue;
            }
            
            String category = categorizeResource(resourceType);
            categoryGroups.computeIfAbsent(category, k -> new ArrayList<>()).add(resource);
        }
        
        // Sort categories by logical order
        return categoryGroups.entrySet().stream()
                .sorted((e1, e2) -> {
                    String c1 = e1.getKey();
                    String c2 = e2.getKey();
                    
                    // Define category priority order
                    List<String> categoryOrder = Arrays.asList(
                            "Network", "Security", "Compute", "Container", 
                            "Database", "Storage", "Load_Balancing", "Monitoring", "Other"
                    );
                    
                    int idx1 = categoryOrder.indexOf(c1);
                    int idx2 = categoryOrder.indexOf(c2);
                    
                    if (idx1 == -1) idx1 = categoryOrder.size();
                    if (idx2 == -1) idx2 = categoryOrder.size();
                    
                    return Integer.compare(idx1, idx2);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
    
    /**
     * Categorize a resource type into a logical category
     */
    private String categorizeResource(String resourceType) {
        for (Map.Entry<String, String[]> entry : RESOURCE_CATEGORIES.entrySet()) {
            String category = entry.getKey();
            String[] patterns = entry.getValue();
            
            for (String pattern : patterns) {
                if (resourceType.equals(pattern) || resourceType.startsWith(pattern + "_")) {
                    return category;
                }
            }
        }
        
        return "Other";
    }
    
    /**
     * Create a group name from provider and category
     */
    private String createGroupName(String provider, String category) {
        String providerName = PROVIDER_GROUPS.getOrDefault(provider, 
                provider.substring(0, 1).toUpperCase() + provider.substring(1));
        
        return String.format("%s - %s", providerName, category.replace("_", " "));
    }
    
    /**
     * Create a descriptive text for a group
     */
    private String createGroupDescription(String provider, String category, int resourceCount) {
        String providerName = PROVIDER_GROUPS.getOrDefault(provider, provider);
        String categoryDesc = getCategoryDescription(category);
        
        return String.format("%s %s (%d resources)", providerName, categoryDesc, resourceCount);
    }
    
    /**
     * Get a human-readable description for a category
     */
    private String getCategoryDescription(String category) {
        return switch (category) {
            case "Compute" -> "compute resources (instances, functions, containers)";
            case "Storage" -> "storage resources (buckets, volumes, file systems)";
            case "Network" -> "networking resources (VPCs, subnets, gateways)";
            case "Database" -> "database resources (RDS, DynamoDB, caches)";
            case "Container" -> "container orchestration resources (EKS, Kubernetes, Helm)";
            case "Security" -> "security and identity resources (IAM, KMS, certificates)";
            case "Monitoring" -> "monitoring and logging resources (CloudWatch, SNS)";
            case "Load_Balancing" -> "load balancing resources (ALB, ELB, target groups)";
            default -> "miscellaneous resources";
        };
    }
    
    /**
     * Optimize group order based on dependencies
     */
    private List<String> optimizeGroupOrder(List<String> originalOrder, 
                                          Map<String, List<String>> groupedResources,
                                          DependencyMap dependencies) {
        
        // For now, use the original order but could be enhanced with dependency analysis
        // This is a placeholder for more sophisticated dependency-based ordering
        
        List<String> optimizedOrder = new ArrayList<>(originalOrder);
        
        // Move network groups to the beginning (infrastructure foundation)
        optimizedOrder.sort((g1, g2) -> {
            boolean g1IsNetwork = g1.toLowerCase().contains("network");
            boolean g2IsNetwork = g2.toLowerCase().contains("network");
            
            if (g1IsNetwork && !g2IsNetwork) return -1;
            if (g2IsNetwork && !g1IsNetwork) return 1;
            
            // Move security groups early
            boolean g1IsSecurity = g1.toLowerCase().contains("security");
            boolean g2IsSecurity = g2.toLowerCase().contains("security");
            
            if (g1IsSecurity && !g2IsSecurity) return -1;
            if (g2IsSecurity && !g1IsSecurity) return 1;
            
            return 0;
        });
        
        logger.debug("Optimized group order: {}", optimizedOrder);
        
        return optimizedOrder;
    }
}