package com.terraform.neo4j.component;

import com.terraform.neo4j.model.FileStructure;
import com.terraform.neo4j.model.TerraformFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Component responsible for scanning Terraform files and analyzing their structure
 */
@Component
public class FileScanner {
    
    private static final Logger logger = LoggerFactory.getLogger(FileScanner.class);
    
    // Regex patterns for Terraform syntax
    private static final Pattern RESOURCE_PATTERN = Pattern.compile(
        "resource\\s+\"([^\"]+)\"\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
        "variable\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);
    private static final Pattern OUTPUT_PATTERN = Pattern.compile(
        "output\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);
    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
        "provider\\s+\"([^\"]+)\"\\s*\\{", Pattern.MULTILINE);
    private static final Pattern TERRAFORM_BLOCK_PATTERN = Pattern.compile(
        "terraform\\s*\\{[^}]*required_providers\\s*\\{[^}]*([^}]+)\\}", Pattern.DOTALL);
    
    /**
     * Analyzes a list of Terraform files and extracts their structure
     * 
     * @param files List of Terraform files to analyze
     * @return FileStructure containing organized metadata about the files
     */
    public FileStructure analyzeFiles(List<TerraformFile> files) {
        logger.info("Starting file structure analysis for {} files", files.size());
        
        Map<String, List<String>> resourcesByFile = new HashMap<>();
        Map<String, List<String>> variablesByFile = new HashMap<>();
        Map<String, List<String>> outputsByFile = new HashMap<>();
        Set<String> providers = new HashSet<>();
        Map<String, String> resourceTypes = new HashMap<>();
        
        for (TerraformFile file : files) {
            try {
                analyzeFile(file, resourcesByFile, variablesByFile, outputsByFile, providers, resourceTypes);
            } catch (Exception e) {
                logger.error("Error analyzing file {}: {}", file.getFileName(), e.getMessage(), e);
                // Continue processing other files
            }
        }
        
        FileStructure structure = new FileStructure(
            resourcesByFile, variablesByFile, outputsByFile, providers, resourceTypes);
        
        logger.info("File analysis complete - Resources: {}, Variables: {}, Outputs: {}, Providers: {}", 
            structure.getTotalResources(), structure.getTotalVariables(), 
            structure.getTotalOutputs(), providers.size());
        
        return structure;
    }
    
    /**
     * Analyzes a single Terraform file
     */
    private void analyzeFile(TerraformFile file, 
                           Map<String, List<String>> resourcesByFile,
                           Map<String, List<String>> variablesByFile,
                           Map<String, List<String>> outputsByFile,
                           Set<String> providers,
                           Map<String, String> resourceTypes) {
        
        String fileName = file.getFileName();
        String content = file.getContent();
        
        logger.debug("Analyzing file: {}", fileName);
        
        // Extract resources
        List<String> resources = extractResources(content, resourceTypes);
        if (!resources.isEmpty()) {
            resourcesByFile.put(fileName, resources);
            logger.debug("Found {} resources in {}", resources.size(), fileName);
        }
        
        // Extract variables
        List<String> variables = extractVariables(content);
        if (!variables.isEmpty()) {
            variablesByFile.put(fileName, variables);
            logger.debug("Found {} variables in {}", variables.size(), fileName);
        }
        
        // Extract outputs
        List<String> outputs = extractOutputs(content);
        if (!outputs.isEmpty()) {
            outputsByFile.put(fileName, outputs);
            logger.debug("Found {} outputs in {}", outputs.size(), fileName);
        }
        
        // Extract providers
        Set<String> fileProviders = extractProviders(content);
        providers.addAll(fileProviders);
        if (!fileProviders.isEmpty()) {
            logger.debug("Found providers in {}: {}", fileName, fileProviders);
        }
    }
    
    /**
     * Extracts resource definitions from Terraform content
     */
    private List<String> extractResources(String content, Map<String, String> resourceTypes) {
        List<String> resources = new ArrayList<>();
        Matcher matcher = RESOURCE_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String resourceType = matcher.group(1);
            String resourceName = matcher.group(2);
            String fullResourceId = resourceType + "." + resourceName;
            
            resources.add(fullResourceId);
            resourceTypes.put(fullResourceId, resourceType);
            
            logger.trace("Found resource: {} (type: {})", fullResourceId, resourceType);
        }
        
        return resources;
    }
    
    /**
     * Extracts variable definitions from Terraform content
     */
    private List<String> extractVariables(String content) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            variables.add(variableName);
            logger.trace("Found variable: {}", variableName);
        }
        
        return variables;
    }
    
    /**
     * Extracts output definitions from Terraform content
     */
    private List<String> extractOutputs(String content) {
        List<String> outputs = new ArrayList<>();
        Matcher matcher = OUTPUT_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String outputName = matcher.group(1);
            outputs.add(outputName);
            logger.trace("Found output: {}", outputName);
        }
        
        return outputs;
    }
    
    /**
     * Extracts provider information from Terraform content
     */
    private Set<String> extractProviders(String content) {
        Set<String> providers = new HashSet<>();
        
        // Extract from provider blocks
        Matcher providerMatcher = PROVIDER_PATTERN.matcher(content);
        while (providerMatcher.find()) {
            String provider = providerMatcher.group(1);
            providers.add(provider);
            logger.trace("Found provider block: {}", provider);
        }
        
        // Extract from terraform required_providers block
        Matcher terraformMatcher = TERRAFORM_BLOCK_PATTERN.matcher(content);
        if (terraformMatcher.find()) {
            String requiredProvidersBlock = terraformMatcher.group(1);
            // Simple extraction of provider names from required_providers
            Pattern providerNamePattern = Pattern.compile("([a-zA-Z0-9_-]+)\\s*=");
            Matcher providerNameMatcher = providerNamePattern.matcher(requiredProvidersBlock);
            while (providerNameMatcher.find()) {
                String provider = providerNameMatcher.group(1);
                providers.add(provider);
                logger.trace("Found required provider: {}", provider);
            }
        }
        
        return providers;
    }
}