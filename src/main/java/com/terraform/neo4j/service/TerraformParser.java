package com.terraform.neo4j.service;

import com.bertramlabs.plugins.hcl4j.HCLParser;
import com.bertramlabs.plugins.hcl4j.HCLParserException;
import com.terraform.neo4j.model.ParsedTerraform;
import com.terraform.neo4j.model.TerraformFile;
import com.terraform.neo4j.model.TerraformResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for parsing Terraform files using HCL4j library.
 * Extracts resources, variables, outputs, and other Terraform constructs from HCL configuration.
 */
@Service
public class TerraformParser {

    private static final Logger logger = LoggerFactory.getLogger(TerraformParser.class);
    private final HCLParser hclParser;

    public TerraformParser() {
        this.hclParser = new HCLParser();
    }

    /**
     * Parses a list of Terraform files and extracts all resources and configurations.
     *
     * @param files List of TerraformFile objects to parse
     * @return ParsedTerraform containing all extracted information
     */
    public ParsedTerraform parse(List<TerraformFile> files) {
        if (files == null || files.isEmpty()) {
            logger.warn("No Terraform files provided for parsing");
            return new ParsedTerraform();
        }

        ParsedTerraform.Builder builder = ParsedTerraform.builder();
        List<TerraformResource> allResources = new ArrayList<>();
        Map<String, Object> allVariables = new HashMap<>();
        Map<String, Object> allOutputs = new HashMap<>();
        Map<String, Object> allProviders = new HashMap<>();

        logger.info("Starting to parse {} Terraform files", files.size());

        for (TerraformFile file : files) {
            try {
                logger.debug("Parsing file: {}", file.getFileName());
                ParsedTerraform fileResult = parseFile(file);
                
                // Aggregate results from all files
                allResources.addAll(fileResult.getResources());
                allVariables.putAll(fileResult.getVariables());
                allOutputs.putAll(fileResult.getOutputs());
                allProviders.putAll(fileResult.getProviders());
                
                // Collect any parse errors
                fileResult.getParseErrors().forEach(builder::addParseError);
                
            } catch (Exception e) {
                String errorMsg = String.format("Failed to parse file %s: %s", 
                    file.getFileName(), e.getMessage());
                logger.error(errorMsg, e);
                builder.addParseError(errorMsg);
            }
        }

        ParsedTerraform result = builder
                .resources(allResources)
                .variables(allVariables)
                .outputs(allOutputs)
                .providers(allProviders)
                .build();

        logger.info("Parsing completed. Found {} resources, {} variables, {} outputs, {} providers with {} errors",
                result.getResourceCount(), 
                result.getVariables().size(),
                result.getOutputs().size(),
                result.getProviders().size(),
                result.getParseErrors().size());

        return result;
    }

    /**
     * Parses a single Terraform file.
     *
     * @param file TerraformFile to parse
     * @return ParsedTerraform containing extracted information from the file
     */
    public ParsedTerraform parseFile(TerraformFile file) {
        if (file == null || file.getContent() == null || file.getContent().trim().isEmpty()) {
            logger.warn("Empty or null Terraform file provided");
            return new ParsedTerraform();
        }

        ParsedTerraform.Builder builder = ParsedTerraform.builder();

        try {
            Map<String, Object> hclAst = hclParser.parse(file.getContent());
            logger.debug("Successfully parsed HCL for file: {}", file.getFileName());

            // Extract different types of Terraform constructs
            extractResources(hclAst, builder);
            extractVariables(hclAst, builder);
            extractOutputs(hclAst, builder);
            extractProviders(hclAst, builder);

        } catch (HCLParserException e) {
            String errorMsg = String.format("HCL parsing error in file %s: %s", 
                file.getFileName(), e.getMessage());
            logger.error(errorMsg, e);
            builder.addParseError(errorMsg);
        } catch (Exception e) {
            String errorMsg = String.format("Unexpected error parsing file %s: %s", 
                file.getFileName(), e.getMessage());
            logger.error(errorMsg, e);
            builder.addParseError(errorMsg);
        }

        return builder.build();
    }

    /**
     * Extracts resource blocks from the HCL AST.
     *
     * @param hclAst The parsed HCL AST
     * @param builder ParsedTerraform builder to add resources to
     */
    @SuppressWarnings("unchecked")
    private void extractResources(Map<String, Object> hclAst, ParsedTerraform.Builder builder) {
        Object resourcesObj = hclAst.get("resource");
        if (resourcesObj instanceof Map) {
            Map<String, Object> resourcesMap = (Map<String, Object>) resourcesObj;
            
            for (Map.Entry<String, Object> resourceTypeEntry : resourcesMap.entrySet()) {
                String resourceType = resourceTypeEntry.getKey();
                
                if (resourceTypeEntry.getValue() instanceof Map) {
                    Map<String, Object> resourceInstances = (Map<String, Object>) resourceTypeEntry.getValue();
                    
                    for (Map.Entry<String, Object> instanceEntry : resourceInstances.entrySet()) {
                        String resourceName = instanceEntry.getKey();
                        
                        try {
                            TerraformResource resource = createTerraformResource(
                                resourceType, resourceName, instanceEntry.getValue());
                            builder.addResource(resource);
                            logger.debug("Extracted resource: {}.{}", resourceType, resourceName);
                        } catch (Exception e) {
                            String errorMsg = String.format("Failed to extract resource %s.%s: %s", 
                                resourceType, resourceName, e.getMessage());
                            logger.warn(errorMsg, e);
                            builder.addParseError(errorMsg);
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a TerraformResource from parsed HCL data.
     *
     * @param type Resource type
     * @param name Resource name
     * @param resourceData Resource configuration data
     * @return TerraformResource object
     */
    @SuppressWarnings("unchecked")
    private TerraformResource createTerraformResource(String type, String name, Object resourceData) {
        Map<String, Object> arguments = new HashMap<>();
        
        if (resourceData instanceof Map) {
            Map<String, Object> resourceMap = (Map<String, Object>) resourceData;
            arguments.putAll(resourceMap);
        } else if (resourceData instanceof List) {
            // Handle case where resource data is a list (multiple configurations)
            List<?> resourceList = (List<?>) resourceData;
            if (!resourceList.isEmpty() && resourceList.get(0) instanceof Map) {
                arguments.putAll((Map<String, Object>) resourceList.get(0));
            }
        }

        return TerraformResource.builder()
                .type(type)
                .name(name)
                .arguments(arguments)
                .build();
    }

    /**
     * Extracts variable blocks from the HCL AST.
     *
     * @param hclAst The parsed HCL AST
     * @param builder ParsedTerraform builder to add variables to
     */
    @SuppressWarnings("unchecked")
    private void extractVariables(Map<String, Object> hclAst, ParsedTerraform.Builder builder) {
        Object variablesObj = hclAst.get("variable");
        if (variablesObj instanceof Map) {
            Map<String, Object> variablesMap = (Map<String, Object>) variablesObj;
            builder.variables(variablesMap);
            logger.debug("Extracted {} variables", variablesMap.size());
        }
    }

    /**
     * Extracts output blocks from the HCL AST.
     *
     * @param hclAst The parsed HCL AST
     * @param builder ParsedTerraform builder to add outputs to
     */
    @SuppressWarnings("unchecked")
    private void extractOutputs(Map<String, Object> hclAst, ParsedTerraform.Builder builder) {
        Object outputsObj = hclAst.get("output");
        if (outputsObj instanceof Map) {
            Map<String, Object> outputsMap = (Map<String, Object>) outputsObj;
            builder.outputs(outputsMap);
            logger.debug("Extracted {} outputs", outputsMap.size());
        }
    }

    /**
     * Extracts provider blocks from the HCL AST.
     *
     * @param hclAst The parsed HCL AST
     * @param builder ParsedTerraform builder to add providers to
     */
    @SuppressWarnings("unchecked")
    private void extractProviders(Map<String, Object> hclAst, ParsedTerraform.Builder builder) {
        Object providersObj = hclAst.get("provider");
        if (providersObj instanceof Map) {
            Map<String, Object> providersMap = (Map<String, Object>) providersObj;
            builder.providers(providersMap);
            logger.debug("Extracted {} providers", providersMap.size());
        }
    }

    /**
     * Validates if the provided content is valid HCL.
     *
     * @param hclContent HCL content to validate
     * @return true if valid HCL, false otherwise
     */
    public boolean isValidHCL(String hclContent) {
        if (hclContent == null || hclContent.trim().isEmpty()) {
            return false;
        }

        try {
            hclParser.parse(hclContent);
            return true;
        } catch (HCLParserException e) {
            logger.debug("Invalid HCL content: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.debug("Error validating HCL content: {}", e.getMessage());
            return false;
        }
    }
}