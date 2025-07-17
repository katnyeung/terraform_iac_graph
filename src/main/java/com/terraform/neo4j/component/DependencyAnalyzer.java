package com.terraform.neo4j.component;

import com.terraform.neo4j.model.DependencyMap;
import com.terraform.neo4j.model.TerraformFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Component responsible for analyzing dependencies between Terraform resources.
 * Parses Terraform files to identify resource references, module dependencies,
 * variable usage, and output consumption.
 */
@Component
public class DependencyAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(DependencyAnalyzer.class);
    
    // Regex patterns for different types of references
    private static final Pattern RESOURCE_REFERENCE_PATTERN = 
        Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    
    private static final Pattern MODULE_REFERENCE_PATTERN = 
        Pattern.compile("\\bmodule\\.([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    
    private static final Pattern VARIABLE_REFERENCE_PATTERN = 
        Pattern.compile("\\bvar\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    
    private static final Pattern OUTPUT_REFERENCE_PATTERN = 
        Pattern.compile("\\boutput\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    
    private static final Pattern DATA_REFERENCE_PATTERN = 
        Pattern.compile("\\bdata\\.([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    
    private static final Pattern LOCAL_REFERENCE_PATTERN = 
        Pattern.compile("\\blocal\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    
    // Pattern to identify resource definitions
    private static final Pattern RESOURCE_DEFINITION_PATTERN = 
        Pattern.compile("^\\s*resource\\s+\"([^\"]+)\"\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);
    
    private static final Pattern MODULE_DEFINITION_PATTERN = 
        Pattern.compile("^\\s*module\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);
    
    private static final Pattern VARIABLE_DEFINITION_PATTERN = 
        Pattern.compile("^\\s*variable\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);
    
    private static final Pattern OUTPUT_DEFINITION_PATTERN = 
        Pattern.compile("^\\s*output\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);
    
    private static final Pattern DATA_DEFINITION_PATTERN = 
        Pattern.compile("^\\s*data\\s+\"([^\"]+)\"\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);

    /**
     * Build a comprehensive dependency map from a list of Terraform files
     */
    public DependencyMap buildDependencyMap(List<TerraformFile> files) {
        logger.info("Starting dependency analysis for {} files", files.size());
        
        Map<String, Set<String>> resourceDependencies = new HashMap<>();
        Map<String, Set<String>> variableUsage = new HashMap<>();
        Map<String, Set<String>> outputReferences = new HashMap<>();
        Map<String, Set<String>> moduleReferences = new HashMap<>();
        Map<String, Set<String>> implicitDependencies = new HashMap<>();
        
        // First pass: identify all defined resources, modules, variables, outputs
        Map<String, String> definedResources = new HashMap<>(); // resource_name -> resource_type
        Set<String> definedModules = new HashSet<>();
        Set<String> definedVariables = new HashSet<>();
        Set<String> definedOutputs = new HashSet<>();
        Set<String> definedDataSources = new HashSet<>();
        
        for (TerraformFile file : files) {
            identifyDefinitions(file, definedResources, definedModules, definedVariables, definedOutputs, definedDataSources);
        }
        
        logger.debug("Found {} resources, {} modules, {} variables, {} outputs, {} data sources",
                definedResources.size(), definedModules.size(), definedVariables.size(), 
                definedOutputs.size(), definedDataSources.size());
        
        // Second pass: analyze dependencies within each file
        for (TerraformFile file : files) {
            analyzeDependenciesInFile(file, definedResources, definedModules, definedVariables, 
                    definedOutputs, definedDataSources, resourceDependencies, variableUsage, 
                    outputReferences, moduleReferences, implicitDependencies);
        }
        
        DependencyMap dependencyMap = new DependencyMap(
                resourceDependencies, variableUsage, outputReferences, 
                moduleReferences, implicitDependencies
        );
        
        logger.info("Dependency analysis completed. Total dependencies: {}", dependencyMap.getTotalDependencies());
        
        return dependencyMap;
    }
    
    /**
     * Identify all resource, module, variable, and output definitions in a file
     */
    private void identifyDefinitions(TerraformFile file, Map<String, String> definedResources,
                                   Set<String> definedModules, Set<String> definedVariables,
                                   Set<String> definedOutputs, Set<String> definedDataSources) {
        
        String content = file.getContent();
        
        // Find resource definitions
        Matcher resourceMatcher = RESOURCE_DEFINITION_PATTERN.matcher(content);
        while (resourceMatcher.find()) {
            String resourceType = resourceMatcher.group(1);
            String resourceName = resourceMatcher.group(2);
            String resourceId = resourceType + "." + resourceName;
            definedResources.put(resourceId, resourceType);
        }
        
        // Find module definitions
        Matcher moduleMatcher = MODULE_DEFINITION_PATTERN.matcher(content);
        while (moduleMatcher.find()) {
            String moduleName = moduleMatcher.group(1);
            definedModules.add(moduleName);
        }
        
        // Find variable definitions
        Matcher variableMatcher = VARIABLE_DEFINITION_PATTERN.matcher(content);
        while (variableMatcher.find()) {
            String variableName = variableMatcher.group(1);
            definedVariables.add(variableName);
        }
        
        // Find output definitions
        Matcher outputMatcher = OUTPUT_DEFINITION_PATTERN.matcher(content);
        while (outputMatcher.find()) {
            String outputName = outputMatcher.group(1);
            definedOutputs.add(outputName);
        }
        
        // Find data source definitions
        Matcher dataMatcher = DATA_DEFINITION_PATTERN.matcher(content);
        while (dataMatcher.find()) {
            String dataType = dataMatcher.group(1);
            String dataName = dataMatcher.group(2);
            String dataId = "data." + dataType + "." + dataName;
            definedDataSources.add(dataId);
        }
    }
    
    /**
     * Analyze dependencies within a single Terraform file
     */
    private void analyzeDependenciesInFile(TerraformFile file, Map<String, String> definedResources,
                                         Set<String> definedModules, Set<String> definedVariables,
                                         Set<String> definedOutputs, Set<String> definedDataSources,
                                         Map<String, Set<String>> resourceDependencies,
                                         Map<String, Set<String>> variableUsage,
                                         Map<String, Set<String>> outputReferences,
                                         Map<String, Set<String>> moduleReferences,
                                         Map<String, Set<String>> implicitDependencies) {
        
        String content = file.getContent();
        
        // Parse the file into resource blocks for context-aware analysis
        List<ResourceBlock> resourceBlocks = parseResourceBlocks(content);
        
        for (ResourceBlock block : resourceBlocks) {
            String currentResource = block.getResourceId();
            
            // Analyze resource references within this block
            analyzeResourceReferences(block, currentResource, definedResources, resourceDependencies);
            
            // Analyze module references
            analyzeModuleReferences(block, currentResource, definedModules, moduleReferences);
            
            // Analyze variable usage
            analyzeVariableUsage(block, currentResource, definedVariables, variableUsage);
            
            // Analyze output references
            analyzeOutputReferences(block, currentResource, definedOutputs, outputReferences);
            
            // Analyze implicit dependencies (security groups, subnets, etc.)
            analyzeImplicitDependencies(block, currentResource, definedResources, implicitDependencies);
        }
    }
    
    /**
     * Parse content into resource blocks for context-aware analysis
     */
    private List<ResourceBlock> parseResourceBlocks(String content) {
        List<ResourceBlock> blocks = new ArrayList<>();
        
        // Find all resource definitions first
        Matcher resourceMatcher = RESOURCE_DEFINITION_PATTERN.matcher(content);
        while (resourceMatcher.find()) {
            String resourceType = resourceMatcher.group(1);
            String resourceName = resourceMatcher.group(2);
            String resourceId = resourceType + "." + resourceName;
            
            // Find the start and end of this resource block
            int startPos = resourceMatcher.start();
            int endPos = findResourceBlockEnd(content, startPos);
            
            if (endPos > startPos) {
                String blockContent = content.substring(startPos, endPos);
                ResourceBlock block = new ResourceBlock(resourceId);
                block.addContent(blockContent);
                blocks.add(block);
                
                logger.debug("Found resource block: {} with {} characters", resourceId, blockContent.length());
            }
        }
        
        logger.debug("Parsed {} resource blocks from content", blocks.size());
        return blocks;
    }
    
    /**
     * Find the end position of a resource block by counting braces
     */
    private int findResourceBlockEnd(String content, int startPos) {
        int braceCount = 0;
        boolean foundFirstBrace = false;
        
        for (int i = startPos; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (c == '{') {
                braceCount++;
                foundFirstBrace = true;
            } else if (c == '}') {
                braceCount--;
                if (foundFirstBrace && braceCount == 0) {
                    return i + 1; // Include the closing brace
                }
            }
        }
        
        return content.length(); // If no matching brace found, return end of content
    }
    
    private int countChar(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }
    
    /**
     * Analyze resource references within a resource block
     */
    private void analyzeResourceReferences(ResourceBlock block, String currentResource,
                                         Map<String, String> definedResources,
                                         Map<String, Set<String>> resourceDependencies) {
        
        Matcher matcher = RESOURCE_REFERENCE_PATTERN.matcher(block.getContent());
        while (matcher.find()) {
            String resourceType = matcher.group(1);
            String resourceName = matcher.group(2);
            String referencedResource = resourceType + "." + resourceName;
            
            // Only add if the referenced resource is actually defined
            if (definedResources.containsKey(referencedResource)) {
                resourceDependencies.computeIfAbsent(currentResource, k -> new HashSet<>())
                        .add(referencedResource);
            }
        }
        
        // Also check for data source references
        Matcher dataMatcher = DATA_REFERENCE_PATTERN.matcher(block.getContent());
        while (dataMatcher.find()) {
            String dataType = dataMatcher.group(1);
            String dataName = dataMatcher.group(2);
            String referencedData = "data." + dataType + "." + dataName;
            
            resourceDependencies.computeIfAbsent(currentResource, k -> new HashSet<>())
                    .add(referencedData);
        }
    }
    
    /**
     * Analyze module references within a resource block
     */
    private void analyzeModuleReferences(ResourceBlock block, String currentResource,
                                       Set<String> definedModules,
                                       Map<String, Set<String>> moduleReferences) {
        
        Matcher matcher = MODULE_REFERENCE_PATTERN.matcher(block.getContent());
        while (matcher.find()) {
            String moduleName = matcher.group(1);
            String outputName = matcher.group(2);
            String moduleRef = "module." + moduleName + "." + outputName;
            
            // Only add if the module is actually defined
            if (definedModules.contains(moduleName)) {
                moduleReferences.computeIfAbsent(currentResource, k -> new HashSet<>())
                        .add(moduleRef);
            }
        }
    }
    
    /**
     * Analyze variable usage within a resource block
     */
    private void analyzeVariableUsage(ResourceBlock block, String currentResource,
                                    Set<String> definedVariables,
                                    Map<String, Set<String>> variableUsage) {
        
        Matcher matcher = VARIABLE_REFERENCE_PATTERN.matcher(block.getContent());
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String variableRef = "var." + variableName;
            
            // Only add if the variable is actually defined
            if (definedVariables.contains(variableName)) {
                variableUsage.computeIfAbsent(currentResource, k -> new HashSet<>())
                        .add(variableRef);
            }
        }
        
        // Also check for local references
        Matcher localMatcher = LOCAL_REFERENCE_PATTERN.matcher(block.getContent());
        while (localMatcher.find()) {
            String localName = localMatcher.group(1);
            String localRef = "local." + localName;
            
            variableUsage.computeIfAbsent(currentResource, k -> new HashSet<>())
                    .add(localRef);
        }
    }
    
    /**
     * Analyze output references within a resource block
     */
    private void analyzeOutputReferences(ResourceBlock block, String currentResource,
                                       Set<String> definedOutputs,
                                       Map<String, Set<String>> outputReferences) {
        
        Matcher matcher = OUTPUT_REFERENCE_PATTERN.matcher(block.getContent());
        while (matcher.find()) {
            String outputName = matcher.group(1);
            String outputRef = "output." + outputName;
            
            // Only add if the output is actually defined
            if (definedOutputs.contains(outputName)) {
                outputReferences.computeIfAbsent(currentResource, k -> new HashSet<>())
                        .add(outputRef);
            }
        }
    }
    
    /**
     * Analyze implicit dependencies (security groups, subnets, VPCs, etc.)
     */
    private void analyzeImplicitDependencies(ResourceBlock block, String currentResource,
                                           Map<String, String> definedResources,
                                           Map<String, Set<String>> implicitDependencies) {
        
        String content = block.getContent().toLowerCase();
        
        // Common implicit dependency patterns
        Map<String, String[]> implicitPatterns = Map.of(
                "security_group", new String[]{"aws_security_group", "aws_vpc_security_group_ingress_rule"},
                "subnet", new String[]{"aws_subnet", "aws_db_subnet_group"},
                "vpc", new String[]{"aws_vpc", "aws_default_vpc"},
                "key_pair", new String[]{"aws_key_pair"},
                "iam_role", new String[]{"aws_iam_role", "aws_iam_instance_profile"}
        );
        
        for (Map.Entry<String, String[]> entry : implicitPatterns.entrySet()) {
            String pattern = entry.getKey();
            String[] resourceTypes = entry.getValue();
            
            if (content.contains(pattern)) {
                // Find resources of these types that might be implicitly referenced
                for (Map.Entry<String, String> resource : definedResources.entrySet()) {
                    String resourceId = resource.getKey();
                    String resourceType = resource.getValue();
                    
                    for (String targetType : resourceTypes) {
                        if (resourceType.equals(targetType) && !resourceId.equals(currentResource)) {
                            implicitDependencies.computeIfAbsent(currentResource, k -> new HashSet<>())
                                    .add(resourceId);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Helper class to represent a resource block with its content
     */
    private static class ResourceBlock {
        private final String resourceId;
        private final StringBuilder content;
        
        public ResourceBlock(String resourceId) {
            this.resourceId = resourceId;
            this.content = new StringBuilder();
        }
        
        public void addLine(String line) {
            content.append(line).append("\n");
        }
        
        public void addContent(String blockContent) {
            content.append(blockContent);
        }
        
        public String getResourceId() {
            return resourceId;
        }
        
        public String getContent() {
            return content.toString();
        }
    }
}