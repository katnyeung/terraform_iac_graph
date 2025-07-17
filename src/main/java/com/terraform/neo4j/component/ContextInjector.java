package com.terraform.neo4j.component;

import com.terraform.neo4j.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Component responsible for injecting contextual information into Terraform content.
 * Adds file boundaries, cross-reference comments, relationship hints, and infrastructure overview.
 */
@Component
public class ContextInjector {
    
    private static final Logger logger = LoggerFactory.getLogger(ContextInjector.class);
    
    private static final String FILE_BOUNDARY_SEPARATOR = "=" .repeat(80);
    private static final String SECTION_SEPARATOR = "-".repeat(40);
    
    /**
     * Add contextual information to grouped resources and dependencies
     */
    public ContextualizedContent addContext(LogicalGroups groups, DependencyMap dependencies, 
                                          List<TerraformFile> originalFiles) {
        logger.info("Starting context injection for {} groups and {} files", 
            groups.getTotalGroups(), originalFiles.size());
        
        // Generate infrastructure overview
        String infrastructureOverview = generateInfrastructureOverview(groups, dependencies);
        
        // Add file boundaries and context to original content
        Map<String, String> fileContentsWithBoundaries = addFileBoundaries(originalFiles);
        
        // Generate cross-reference comments
        Map<String, List<String>> crossReferenceComments = generateCrossReferenceComments(dependencies);
        
        // Generate relationship hints
        Map<String, List<String>> relationshipHints = generateRelationshipHints(groups, dependencies);
        
        ContextualizedContent content = new ContextualizedContent(
            infrastructureOverview,
            fileContentsWithBoundaries,
            crossReferenceComments,
            relationshipHints,
            groups,
            dependencies
        );
        
        // Add metadata
        content.addMetadata("total_files", originalFiles.size());
        content.addMetadata("total_groups", groups.getTotalGroups());
        content.addMetadata("total_dependencies", dependencies.getTotalDependencies());
        content.addMetadata("context_injection_timestamp", System.currentTimeMillis());
        
        logger.info("Context injection completed. Overview: {} chars, Cross-refs: {}, Hints: {}", 
            infrastructureOverview.length(), content.getTotalCrossReferences(), content.getTotalRelationshipHints());
        
        return content;
    }
    
    /**
     * Generate a comprehensive infrastructure overview
     */
    private String generateInfrastructureOverview(LogicalGroups groups, DependencyMap dependencies) {
        StringBuilder overview = new StringBuilder();
        
        overview.append("# TERRAFORM INFRASTRUCTURE OVERVIEW\n");
        overview.append(FILE_BOUNDARY_SEPARATOR).append("\n\n");
        
        // Summary statistics
        overview.append("## Infrastructure Summary\n");
        overview.append(String.format("- Total Resource Groups: %d\n", groups.getTotalGroups()));
        overview.append(String.format("- Total Resources: %d\n", groups.getTotalResources()));
        overview.append(String.format("- Total Dependencies: %d\n", dependencies.getTotalDependencies()));
        overview.append("\n");
        
        // Group breakdown
        overview.append("## Resource Groups\n");
        for (String groupName : groups.getGroupOrder()) {
            List<String> resources = groups.getResourcesInGroup(groupName);
            String description = groups.getGroupDescriptions().get(groupName);
            
            overview.append(String.format("### %s\n", groupName));
            overview.append(String.format("- %s\n", description));
            overview.append(String.format("- Resources: %s\n", 
                resources.stream().limit(5).collect(Collectors.joining(", "))));
            if (resources.size() > 5) {
                overview.append(String.format("  ... and %d more\n", resources.size() - 5));
            }
            overview.append("\n");
        }
        
        // Key relationships
        overview.append("## Key Infrastructure Relationships\n");
        Map<String, Set<String>> resourceDeps = dependencies.getResourceDependencies();
        if (resourceDeps != null && !resourceDeps.isEmpty()) {
            int relationshipCount = 0;
            for (Map.Entry<String, Set<String>> entry : resourceDeps.entrySet()) {
                if (relationshipCount >= 10) break; // Limit to top 10 relationships
                
                String resource = entry.getKey();
                Set<String> deps = entry.getValue();
                if (!deps.isEmpty()) {
                    overview.append(String.format("- %s depends on: %s\n", 
                        resource, deps.stream().limit(3).collect(Collectors.joining(", "))));
                    relationshipCount++;
                }
            }
        }
        overview.append("\n");
        
        // Analysis hints for LLM
        overview.append("## Analysis Guidelines\n");
        overview.append("- Focus on explicit resource references (resource.name.attribute)\n");
        overview.append("- Identify module dependencies (module.name.output)\n");
        overview.append("- Map security group and network relationships\n");
        overview.append("- Track storage and compute resource connections\n");
        overview.append("- Note provider-specific relationship patterns\n");
        overview.append("\n");
        
        overview.append(FILE_BOUNDARY_SEPARATOR).append("\n\n");
        
        return overview.toString();
    }
    
    /**
     * Add clear file boundaries to original Terraform content
     */
    private Map<String, String> addFileBoundaries(List<TerraformFile> files) {
        Map<String, String> boundaryContent = new LinkedHashMap<>();
        
        for (TerraformFile file : files) {
            StringBuilder content = new StringBuilder();
            
            // File header with boundary
            content.append(String.format("# FILE: %s\n", file.getFileName()));
            content.append(FILE_BOUNDARY_SEPARATOR).append("\n");
            content.append(String.format("# Path: %s\n", file.getFileName()));
            content.append(String.format("# Size: %d characters\n", file.getContent().length()));
            content.append(SECTION_SEPARATOR).append("\n\n");
            
            // Original file content
            content.append(file.getContent());
            
            // Ensure content ends with newlines
            if (!file.getContent().endsWith("\n")) {
                content.append("\n");
            }
            content.append("\n");
            
            // File footer
            content.append(SECTION_SEPARATOR).append("\n");
            content.append(String.format("# END FILE: %s\n", file.getFileName()));
            content.append(FILE_BOUNDARY_SEPARATOR).append("\n\n");
            
            boundaryContent.put(file.getFileName(), content.toString());
        }
        
        logger.debug("Added file boundaries for {} files", files.size());
        return boundaryContent;
    }
    
    /**
     * Generate cross-reference comments for resources
     */
    private Map<String, List<String>> generateCrossReferenceComments(DependencyMap dependencies) {
        Map<String, List<String>> comments = new HashMap<>();
        
        // Generate comments for resource dependencies
        if (dependencies.getResourceDependencies() != null) {
            for (Map.Entry<String, Set<String>> entry : dependencies.getResourceDependencies().entrySet()) {
                String resource = entry.getKey();
                Set<String> deps = entry.getValue();
                
                List<String> resourceComments = new ArrayList<>();
                resourceComments.add(String.format("# DEPENDENCIES: %s depends on %d resources", 
                    resource, deps.size()));
                
                for (String dep : deps) {
                    resourceComments.add(String.format("#   -> %s", dep));
                }
                
                comments.put(resource, resourceComments);
            }
        }
        
        // Generate comments for module references
        if (dependencies.getModuleReferences() != null) {
            for (Map.Entry<String, Set<String>> entry : dependencies.getModuleReferences().entrySet()) {
                String resource = entry.getKey();
                Set<String> moduleRefs = entry.getValue();
                
                List<String> moduleComments = comments.computeIfAbsent(resource, k -> new ArrayList<>());
                moduleComments.add(String.format("# MODULE_REFS: %s references %d module outputs", 
                    resource, moduleRefs.size()));
                
                for (String moduleRef : moduleRefs) {
                    moduleComments.add(String.format("#   -> %s", moduleRef));
                }
            }
        }
        
        // Generate comments for variable usage
        if (dependencies.getVariableUsage() != null) {
            for (Map.Entry<String, Set<String>> entry : dependencies.getVariableUsage().entrySet()) {
                String resource = entry.getKey();
                Set<String> variables = entry.getValue();
                
                if (variables.size() > 3) { // Only comment on resources with significant variable usage
                    List<String> varComments = comments.computeIfAbsent(resource, k -> new ArrayList<>());
                    varComments.add(String.format("# VARIABLES: %s uses %d variables/locals", 
                        resource, variables.size()));
                }
            }
        }
        
        logger.debug("Generated cross-reference comments for {} resources", comments.size());
        return comments;
    }
    
    /**
     * Generate relationship hints for LLM analysis
     */
    private Map<String, List<String>> generateRelationshipHints(LogicalGroups groups, DependencyMap dependencies) {
        Map<String, List<String>> hints = new HashMap<>();
        
        // Generate hints based on logical groups
        for (String groupName : groups.getGroupOrder()) {
            List<String> resources = groups.getResourcesInGroup(groupName);
            
            if (resources.size() > 1) {
                List<String> groupHints = new ArrayList<>();
                groupHints.add(String.format("# GROUP_HINT: Resources in '%s' are logically related", groupName));
                groupHints.add(String.format("#   Resources: %s", 
                    resources.stream().limit(5).collect(Collectors.joining(", "))));
                
                // Add specific relationship hints based on group type
                if (groupName.toLowerCase().contains("network")) {
                    groupHints.add("#   Expected relationships: VPC -> Subnets -> Security Groups -> Route Tables");
                } else if (groupName.toLowerCase().contains("compute")) {
                    groupHints.add("#   Expected relationships: Launch Templates -> Auto Scaling Groups -> Load Balancers");
                } else if (groupName.toLowerCase().contains("container")) {
                    groupHints.add("#   Expected relationships: EKS Cluster -> Node Groups -> Helm Releases");
                } else if (groupName.toLowerCase().contains("storage")) {
                    groupHints.add("#   Expected relationships: Storage -> Mount Targets -> Compute Resources");
                }
                
                // Add hints for the first resource in each group
                if (!resources.isEmpty()) {
                    hints.put(resources.get(0), groupHints);
                }
            }
        }
        
        // Generate hints for high-dependency resources
        if (dependencies.getResourceDependencies() != null) {
            for (Map.Entry<String, Set<String>> entry : dependencies.getResourceDependencies().entrySet()) {
                String resource = entry.getKey();
                Set<String> deps = entry.getValue();
                
                if (deps.size() >= 3) { // Resources with many dependencies
                    List<String> depHints = hints.computeIfAbsent(resource, k -> new ArrayList<>());
                    depHints.add(String.format("# DEPENDENCY_HINT: %s has %d dependencies - likely a central resource", 
                        resource, deps.size()));
                    depHints.add("#   Consider relationships: DEPENDS_ON, PROVIDES_STORAGE_FOR, DEPLOYED_ON, PROTECTED_BY");
                }
            }
        }
        
        // Generate hints for implicit relationships
        if (dependencies.getImplicitDependencies() != null) {
            for (Map.Entry<String, Set<String>> entry : dependencies.getImplicitDependencies().entrySet()) {
                String resource = entry.getKey();
                Set<String> implicitDeps = entry.getValue();
                
                if (!implicitDeps.isEmpty()) {
                    List<String> implicitHints = hints.computeIfAbsent(resource, k -> new ArrayList<>());
                    implicitHints.add(String.format("# IMPLICIT_HINT: %s may have implicit relationships with %d resources", 
                        resource, implicitDeps.size()));
                    implicitHints.add("#   Check for: Security Group rules, Subnet associations, VPC relationships");
                }
            }
        }
        
        logger.debug("Generated relationship hints for {} resources", hints.size());
        return hints;
    }
}