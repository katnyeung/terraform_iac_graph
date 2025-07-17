package com.terraform.neo4j.component;

import com.terraform.neo4j.model.ContextualizedContent;
import com.terraform.neo4j.model.MergedTerraformText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Component responsible for formatting merged Terraform content for optimal LLM readability.
 * Creates hierarchical organization with clear sections, consistent indentation, and spacing.
 */
@Component
public class TextFormatter {
    
    private static final Logger logger = LoggerFactory.getLogger(TextFormatter.class);
    
    private static final String MAIN_SEPARATOR = "=".repeat(100);
    private static final String SECTION_SEPARATOR = "-".repeat(80);
    private static final String SUBSECTION_SEPARATOR = "-".repeat(40);
    private static final int MAX_CONTENT_LENGTH = 500000; // 500KB limit for LLM processing
    
    /**
     * Format contextualized content for optimal LLM readability
     */
    public MergedTerraformText formatForLLM(ContextualizedContent content) {
        logger.info("Starting text formatting for LLM analysis");
        
        StringBuilder formattedContent = new StringBuilder();
        
        // 1. Add infrastructure overview at the top
        addInfrastructureOverview(formattedContent, content);
        
        // 2. Add logical group organization
        addLogicalGroupSummary(formattedContent, content);
        
        // 3. Add dependency analysis summary
        addDependencySummary(formattedContent, content);
        
        // 4. Add formatted file contents with context
        addFormattedFileContents(formattedContent, content);
        
        // 5. Add analysis instructions
        addAnalysisInstructions(formattedContent, content);
        
        String finalContent = formattedContent.toString();
        
        // Check content length and truncate if necessary
        if (finalContent.length() > MAX_CONTENT_LENGTH) {
            logger.warn("Content length {} exceeds maximum {}, truncating", 
                finalContent.length(), MAX_CONTENT_LENGTH);
            finalContent = truncateContent(finalContent);
        }
        
        // Build the final MergedTerraformText object
        MergedTerraformText mergedText = MergedTerraformText.builder()
                .mergedContent(finalContent)
                .fileNames(new ArrayList<>(content.getFileContentsWithBoundaries().keySet()))
                .fileBoundaries(content.getFileContentsWithBoundaries())
                .crossReferences(content.getDependencies())
                .resourceGroups(content.getLogicalGroups())
                .totalResources(content.getLogicalGroups().getTotalResources())
                .providers(extractProviders(content))
                .addMetadata("formatting_timestamp", System.currentTimeMillis())
                .addMetadata("original_length", formattedContent.length())
                .addMetadata("final_length", finalContent.length())
                .addMetadata("truncated", finalContent.length() < formattedContent.length())
                .addMetadata("total_groups", content.getLogicalGroups().getTotalGroups())
                .addMetadata("total_dependencies", content.getDependencies().getTotalDependencies())
                .build();
        
        logger.info("Text formatting completed. Final content: {} characters, {} files, {} resources", 
            finalContent.length(), mergedText.getFileNames().size(), mergedText.getTotalResources());
        
        return mergedText;
    }
    
    /**
     * Add infrastructure overview section
     */
    private void addInfrastructureOverview(StringBuilder content, ContextualizedContent contextContent) {
        content.append(MAIN_SEPARATOR).append("\n");
        content.append("# TERRAFORM INFRASTRUCTURE ANALYSIS\n");
        content.append(MAIN_SEPARATOR).append("\n\n");
        
        if (contextContent.getInfrastructureOverview() != null) {
            content.append(contextContent.getInfrastructureOverview());
        }
        
        content.append("\n");
    }
    
    /**
     * Add logical group summary
     */
    private void addLogicalGroupSummary(StringBuilder content, ContextualizedContent contextContent) {
        content.append(SECTION_SEPARATOR).append("\n");
        content.append("# LOGICAL RESOURCE ORGANIZATION\n");
        content.append(SECTION_SEPARATOR).append("\n\n");
        
        var groups = contextContent.getLogicalGroups();
        if (groups != null && groups.getGroupOrder() != null) {
            content.append("## Resource Groups by Category\n\n");
            
            for (String groupName : groups.getGroupOrder()) {
                List<String> resources = groups.getResourcesInGroup(groupName);
                String description = groups.getGroupDescriptions().get(groupName);
                
                content.append(String.format("### %s\n", groupName));
                content.append(String.format("**Description:** %s\n\n", description));
                content.append("**Resources:**\n");
                
                for (String resource : resources) {
                    content.append(String.format("- %s\n", resource));
                }
                content.append("\n");
            }
        }
        
        content.append("\n");
    }
    
    /**
     * Add dependency analysis summary
     */
    private void addDependencySummary(StringBuilder content, ContextualizedContent contextContent) {
        content.append(SECTION_SEPARATOR).append("\n");
        content.append("# DEPENDENCY ANALYSIS SUMMARY\n");
        content.append(SECTION_SEPARATOR).append("\n\n");
        
        var dependencies = contextContent.getDependencies();
        if (dependencies != null) {
            // Resource dependencies
            if (dependencies.getResourceDependencies() != null && !dependencies.getResourceDependencies().isEmpty()) {
                content.append("## Key Resource Dependencies\n\n");
                
                dependencies.getResourceDependencies().entrySet().stream()
                        .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                        .limit(15) // Top 15 most connected resources
                        .forEach(entry -> {
                            String resource = entry.getKey();
                            Set<String> deps = entry.getValue();
                            content.append(String.format("**%s** depends on:\n", resource));
                            for (String dep : deps) {
                                content.append(String.format("  - %s\n", dep));
                            }
                            content.append("\n");
                        });
            }
            
            // Module references
            if (dependencies.getModuleReferences() != null && !dependencies.getModuleReferences().isEmpty()) {
                content.append("## Module Dependencies\n\n");
                
                dependencies.getModuleReferences().entrySet().stream()
                        .limit(10)
                        .forEach(entry -> {
                            String resource = entry.getKey();
                            Set<String> moduleRefs = entry.getValue();
                            content.append(String.format("**%s** references modules:\n", resource));
                            for (String moduleRef : moduleRefs) {
                                content.append(String.format("  - %s\n", moduleRef));
                            }
                            content.append("\n");
                        });
            }
        }
        
        content.append("\n");
    }
    
    /**
     * Add formatted file contents with enhanced context
     */
    private void addFormattedFileContents(StringBuilder content, ContextualizedContent contextContent) {
        content.append(MAIN_SEPARATOR).append("\n");
        content.append("# TERRAFORM FILE CONTENTS\n");
        content.append(MAIN_SEPARATOR).append("\n\n");
        
        var fileContents = contextContent.getFileContentsWithBoundaries();
        var crossRefComments = contextContent.getCrossReferenceComments();
        var relationshipHints = contextContent.getRelationshipHints();
        
        if (fileContents != null) {
            // Sort files for consistent ordering (main.tf first, then alphabetically)
            List<String> sortedFiles = fileContents.keySet().stream()
                    .sorted((f1, f2) -> {
                        if (f1.equals("main.tf") && !f2.equals("main.tf")) return -1;
                        if (f2.equals("main.tf") && !f1.equals("main.tf")) return 1;
                        return f1.compareTo(f2);
                    })
                    .collect(Collectors.toList());
            
            for (String fileName : sortedFiles) {
                String fileContent = fileContents.get(fileName);
                
                content.append(SECTION_SEPARATOR).append("\n");
                content.append(String.format("## FILE: %s\n", fileName));
                content.append(SECTION_SEPARATOR).append("\n\n");
                
                // Add cross-reference comments if available
                addCrossReferenceComments(content, fileName, crossRefComments);
                
                // Add relationship hints if available
                addRelationshipHints(content, fileName, relationshipHints);
                
                // Add the actual file content with proper formatting
                content.append("```hcl\n");
                content.append(cleanAndFormatTerraformContent(fileContent));
                content.append("\n```\n\n");
            }
        }
    }
    
    /**
     * Add cross-reference comments for a file
     */
    private void addCrossReferenceComments(StringBuilder content, String fileName, 
                                         Map<String, List<String>> crossRefComments) {
        if (crossRefComments != null && !crossRefComments.isEmpty()) {
            // Find comments related to resources in this file
            boolean hasComments = false;
            StringBuilder comments = new StringBuilder();
            
            for (Map.Entry<String, List<String>> entry : crossRefComments.entrySet()) {
                String resource = entry.getKey();
                List<String> resourceComments = entry.getValue();
                
                // Simple heuristic: if resource name appears in filename or vice versa
                if (resource.toLowerCase().contains(fileName.toLowerCase().replace(".tf", "")) ||
                    fileName.toLowerCase().contains(resource.split("\\.")[0].toLowerCase())) {
                    
                    for (String comment : resourceComments) {
                        comments.append(comment).append("\n");
                    }
                    hasComments = true;
                }
            }
            
            if (hasComments) {
                content.append("### Cross-Reference Analysis\n");
                content.append("```\n");
                content.append(comments.toString());
                content.append("```\n\n");
            }
        }
    }
    
    /**
     * Add relationship hints for a file
     */
    private void addRelationshipHints(StringBuilder content, String fileName, 
                                    Map<String, List<String>> relationshipHints) {
        if (relationshipHints != null && !relationshipHints.isEmpty()) {
            boolean hasHints = false;
            StringBuilder hints = new StringBuilder();
            
            for (Map.Entry<String, List<String>> entry : relationshipHints.entrySet()) {
                String resource = entry.getKey();
                List<String> resourceHints = entry.getValue();
                
                // Simple heuristic: if resource name appears in filename or vice versa
                if (resource.toLowerCase().contains(fileName.toLowerCase().replace(".tf", "")) ||
                    fileName.toLowerCase().contains(resource.split("\\.")[0].toLowerCase())) {
                    
                    for (String hint : resourceHints) {
                        hints.append(hint).append("\n");
                    }
                    hasHints = true;
                }
            }
            
            if (hasHints) {
                content.append("### Relationship Analysis Hints\n");
                content.append("```\n");
                content.append(hints.toString());
                content.append("```\n\n");
            }
        }
    }
    
    /**
     * Add analysis instructions for the LLM
     */
    private void addAnalysisInstructions(StringBuilder content, ContextualizedContent contextContent) {
        content.append(MAIN_SEPARATOR).append("\n");
        content.append("# LLM ANALYSIS INSTRUCTIONS\n");
        content.append(MAIN_SEPARATOR).append("\n\n");
        
        content.append("## Analysis Requirements\n\n");
        content.append("1. **Resource Identification**: Extract every resource with its exact Terraform identifier\n");
        content.append("2. **Relationship Mapping**: Identify ALL relationships including:\n");
        content.append("   - Direct references (resource.name.attribute)\n");
        content.append("   - Module dependencies (module.name.output)\n");
        content.append("   - Implicit dependencies (security groups, subnets, etc.)\n");
        content.append("   - Provider relationships\n");
        content.append("   - Variable usage and output consumption\n\n");
        
        content.append("## Expected Relationship Types\n\n");
        content.append("- **DEPENDS_ON**: Explicit and implicit dependencies\n");
        content.append("- **PROVIDES_STORAGE_FOR**: Storage resources serving compute resources\n");
        content.append("- **DEPLOYED_ON**: Applications deployed on infrastructure\n");
        content.append("- **PROTECTED_BY**: Security group relationships\n");
        content.append("- **MANAGES**: Management and configuration relationships\n");
        content.append("- **ROUTES_TO**: Network routing relationships\n");
        content.append("- **MOUNTS**: File system mounting relationships\n\n");
        
        content.append("## Output Format Required\n\n");
        content.append("```json\n");
        content.append("{\n");
        content.append("  \"resources\": [\n");
        content.append("    {\n");
        content.append("      \"id\": \"exact_terraform_identifier\",\n");
        content.append("      \"type\": \"terraform_resource_type\",\n");
        content.append("      \"name\": \"resource_name\",\n");
        content.append("      \"provider\": \"aws|kubernetes|helm|etc\",\n");
        content.append("      \"properties\": {\n");
        content.append("        \"key_properties\": \"extracted_from_terraform\"\n");
        content.append("      }\n");
        content.append("    }\n");
        content.append("  ],\n");
        content.append("  \"relationships\": [\n");
        content.append("    {\n");
        content.append("      \"source\": \"exact_terraform_identifier\",\n");
        content.append("      \"target\": \"exact_terraform_identifier\",\n");
        content.append("      \"type\": \"RELATIONSHIP_TYPE\",\n");
        content.append("      \"description\": \"detailed_technical_description\",\n");
        content.append("      \"confidence\": 0.95\n");
        content.append("    }\n");
        content.append("  ]\n");
        content.append("}\n");
        content.append("```\n\n");
        
        content.append(MAIN_SEPARATOR).append("\n\n");
    }
    
    /**
     * Clean and format Terraform content for better readability
     */
    private String cleanAndFormatTerraformContent(String content) {
        if (content == null) {
            return "";
        }
        
        // Remove excessive blank lines
        String cleaned = content.replaceAll("\n{3,}", "\n\n");
        
        // Ensure consistent line endings
        cleaned = cleaned.replaceAll("\r\n", "\n");
        
        // Remove trailing whitespace from lines
        cleaned = Arrays.stream(cleaned.split("\n"))
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"));
        
        return cleaned;
    }
    
    /**
     * Extract providers from contextualized content
     */
    private Set<String> extractProviders(ContextualizedContent content) {
        Set<String> providers = new HashSet<>();
        
        if (content.getLogicalGroups() != null && content.getLogicalGroups().getGroupOrder() != null) {
            for (String groupName : content.getLogicalGroups().getGroupOrder()) {
                if (groupName.toLowerCase().contains("aws")) {
                    providers.add("aws");
                } else if (groupName.toLowerCase().contains("kubernetes")) {
                    providers.add("kubernetes");
                } else if (groupName.toLowerCase().contains("helm")) {
                    providers.add("helm");
                } else if (groupName.toLowerCase().contains("azure")) {
                    providers.add("azurerm");
                } else if (groupName.toLowerCase().contains("google")) {
                    providers.add("google");
                }
            }
        }
        
        return providers;
    }
    
    /**
     * Truncate content if it exceeds maximum length
     */
    private String truncateContent(String content) {
        if (content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        
        // Try to truncate at a reasonable boundary (end of a file section)
        String truncated = content.substring(0, MAX_CONTENT_LENGTH);
        int lastSectionEnd = truncated.lastIndexOf(SECTION_SEPARATOR);
        
        if (lastSectionEnd > MAX_CONTENT_LENGTH * 0.8) { // If we can save at least 20%
            truncated = content.substring(0, lastSectionEnd);
        }
        
        truncated += "\n\n" + SECTION_SEPARATOR + "\n";
        truncated += "# CONTENT TRUNCATED DUE TO LENGTH LIMITS\n";
        truncated += "# Original length: " + content.length() + " characters\n";
        truncated += "# Truncated length: " + truncated.length() + " characters\n";
        truncated += SECTION_SEPARATOR + "\n";
        
        return truncated;
    }
}