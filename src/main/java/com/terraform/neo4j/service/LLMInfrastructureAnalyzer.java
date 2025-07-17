package com.terraform.neo4j.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraform.neo4j.model.LLMRelationship;
import com.terraform.neo4j.model.LLMResource;
import com.terraform.neo4j.model.MergedTerraformText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service that uses LLM to analyze Terraform infrastructure from raw text and generate
 * meaningful relationships and insights for Neo4j graph creation.
 * 
 * This service processes MergedTerraformText objects that contain optimized Terraform
 * configurations for LLM analysis, replacing the legacy HCL4j-based parsing approach.
 */
@Service
public class LLMInfrastructureAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(LLMInfrastructureAnalyzer.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${llm.model:gpt-3.5-turbo}")
    private String model;

    @Value("${llm.max.tokens:4000}")
    private int maxTokens;

    @Value("${llm.temperature:0.2}")
    private double temperature;

    public LLMInfrastructureAnalyzer() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyzes Terraform infrastructure using LLM with direct text processing.
     * This is the enhanced method that accepts MergedTerraformText for better analysis.
     *
     * @param mergedTerraformText Merged and formatted Terraform text optimized for LLM
     * @return LLM analysis result with relationships and resources
     * @throws IllegalArgumentException if mergedTerraformText is null or invalid
     */
    public LLMAnalysisResult analyzeInfrastructure(MergedTerraformText mergedTerraformText) {
        if (mergedTerraformText == null) {
            throw new IllegalArgumentException("MergedTerraformText cannot be null");
        }
        
        if (!mergedTerraformText.isReadyForLLM()) {
            logger.warn("MergedTerraformText is not ready for LLM processing: {}", mergedTerraformText.getSummary());
            return new LLMAnalysisResult();
        }

        logger.info("Starting enhanced LLM analysis of Terraform infrastructure with direct text processing");
        logger.info("Processing {} files with {} resources, content length: {} characters", 
                   mergedTerraformText.getFileNames().size(), 
                   mergedTerraformText.getTotalResources(),
                   mergedTerraformText.getContentLength());

        try {
            // Validate content size for LLM processing
            if (mergedTerraformText.getContentLength() > 50000) {
                logger.warn("Large Terraform content ({} chars) may exceed LLM token limits", 
                           mergedTerraformText.getContentLength());
            }

            String prompt = buildEnhancedAnalysisPrompt(mergedTerraformText);
            logger.debug("Built enhanced prompt with {} characters", prompt.length());
            
            String llmResponse = callLLMAPI(prompt);
            logger.info("Received LLM response with {} characters", llmResponse.length());
            logger.debug("Full LLM Response: {}", llmResponse);

            LLMAnalysisResult result = parseLLMResponse(llmResponse);
            
            // Validate and log analysis results
            validateAnalysisResult(result, mergedTerraformText);
            
            logger.info("Enhanced LLM analysis completed. Found {} resources and {} relationships",
                    result.getResources().size(), result.getRelationships().size());

            return result;

        } catch (Exception e) {
            logger.error("Error during enhanced LLM analysis", e);
            // Return empty result instead of failing completely
            return new LLMAnalysisResult();
        }
    }

    /**
     * Validates the LLM analysis result against the input to ensure quality.
     */
    private void validateAnalysisResult(LLMAnalysisResult result, MergedTerraformText input) {
        if (result.getResources().isEmpty() && input.getTotalResources() > 0) {
            logger.warn("LLM analysis found no resources despite {} resources in input", input.getTotalResources());
        }
        
        if (result.getRelationships().isEmpty() && result.getResources().size() > 1) {
            logger.warn("LLM analysis found no relationships despite {} resources", result.getResources().size());
        }
        
        // Check for resource count discrepancy
        int foundResources = result.getResources().size();
        int expectedResources = input.getTotalResources();
        if (foundResources < expectedResources * 0.5) {
            logger.warn("LLM analysis found significantly fewer resources ({}) than expected ({})", 
                       foundResources, expectedResources);
        }
        
        // Log provider coverage
        Set<String> foundProviders = result.getResources().stream()
                .map(LLMResource::getProvider)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        
        Set<String> expectedProviders = input.getProviders();
        if (!foundProviders.containsAll(expectedProviders)) {
            logger.warn("LLM analysis may have missed some providers. Expected: {}, Found: {}", 
                       expectedProviders, foundProviders);
        }
    }



    /**
     * Builds the enhanced analysis prompt for direct Terraform text processing.
     * Utilizes rich metadata from MergedTerraformText for better LLM analysis.
     */
    private String buildEnhancedAnalysisPrompt(MergedTerraformText mergedTerraformText) {
        StringBuilder contextInfo = new StringBuilder();
        
        // Add file information
        contextInfo.append("FILES PROCESSED: ").append(String.join(", ", mergedTerraformText.getFileNames())).append("\n");
        contextInfo.append("TOTAL RESOURCES: ").append(mergedTerraformText.getTotalResources()).append("\n");
        contextInfo.append("PROVIDERS: ").append(String.join(", ", mergedTerraformText.getProviders())).append("\n");
        contextInfo.append("CONTENT LENGTH: ").append(mergedTerraformText.getContentLength()).append(" characters\n");
        
        // Add logical grouping information if available
        if (mergedTerraformText.getResourceGroups() != null && 
            mergedTerraformText.getResourceGroups().getGroupedResources() != null) {
            contextInfo.append("RESOURCE GROUPS: ");
            mergedTerraformText.getResourceGroups().getGroupedResources().keySet()
                .forEach(group -> contextInfo.append(group).append(" "));
            contextInfo.append("\n");
        }
        
        // Add cross-reference information if available
        if (mergedTerraformText.getCrossReferences() != null) {
            contextInfo.append("CROSS-REFERENCES DETECTED: ")
                      .append(mergedTerraformText.getCrossReferences().getTotalDependencies())
                      .append(" dependencies\n");
        }
        
        // Add file boundaries information for better context
        if (mergedTerraformText.getFileBoundaries() != null && !mergedTerraformText.getFileBoundaries().isEmpty()) {
            contextInfo.append("FILE BOUNDARIES: ");
            mergedTerraformText.getFileBoundaries().keySet()
                .forEach(file -> contextInfo.append(file).append(" "));
            contextInfo.append("\n");
        }
        
        // Add metadata information if available
        if (mergedTerraformText.getMetadata() != null && !mergedTerraformText.getMetadata().isEmpty()) {
            contextInfo.append("PROCESSING METADATA: ");
            mergedTerraformText.getMetadata().forEach((key, value) -> 
                contextInfo.append(key).append("=").append(value).append(" "));
            contextInfo.append("\n");
        }
        
        return String.format("""
            You are an expert Terraform infrastructure analyst. Analyze the following complete Terraform configuration and extract ALL resource relationships with precise technical accuracy.

            CONTEXT INFORMATION:
            %s

            TERRAFORM CONFIGURATION:
            ================================================================================
            %s
            ================================================================================

            ANALYSIS REQUIREMENTS:
            1. Identify every resource by its exact Terraform identifier (e.g., "aws_efs_file_system.ai_impact")
            2. Map ALL relationships including:
               - Direct references (resource.name.attribute)
               - Module dependencies (module.name.output)
               - Implicit dependencies (security groups, subnets, etc.)
               - Provider relationships
               - Variable usage and output consumption

            RELATIONSHIP TYPE DEFINITIONS WITH EXAMPLES:
            
            DEPENDS_ON: One resource requires another to exist first
            - Example: aws_instance.web → aws_security_group.web_sg
            - Description: "EC2 instance depends on security group for network access rules"
            
            PROVIDES_STORAGE_FOR: Storage resources serving compute/application resources
            - Example: aws_efs_file_system.shared → aws_instance.web
            - Description: "EFS file system provides persistent storage for EC2 instances"
            
            DEPLOYED_ON: Applications/services deployed on infrastructure platforms
            - Example: helm_release.nginx → module.eks
            - Description: "Nginx Helm chart deployed on EKS cluster infrastructure"
            
            PROTECTED_BY: Resources protected by security mechanisms
            - Example: aws_instance.web → aws_security_group.web_sg
            - Description: "EC2 instance network traffic protected by security group rules"
            
            MANAGES: Management and orchestration relationships
            - Example: kubernetes_storage_class.efs → aws_efs_file_system.shared
            - Description: "Kubernetes storage class manages EFS file system provisioning"
            
            ROUTES_TO: Network traffic routing relationships
            - Example: aws_lb.main → aws_instance.web
            - Description: "Application load balancer routes HTTP traffic to EC2 instances"
            
            MOUNTS: File system mounting relationships
            - Example: aws_efs_mount_target.main → aws_efs_file_system.shared
            - Description: "EFS mount target provides network access point for file system"
            
            CONFIGURES: Configuration and setup relationships
            - Example: aws_iam_role.eks_node → aws_eks_node_group.main
            - Description: "IAM role configures permissions for EKS node group instances"
            
            USES: General resource usage relationships
            - Example: aws_instance.web → aws_subnet.private
            - Description: "EC2 instance uses private subnet for network placement"

            RESOURCE IDENTIFICATION EXAMPLES:
            
            For "resource aws_instance web { ... }":
            {
              "id": "aws_instance.web",
              "type": "aws_instance",
              "name": "web",
              "provider": "aws",
              "properties": {
                "instance_type": "t3.micro",
                "ami": "ami-12345678"
              }
            }
            
            For "resource helm_release cert_manager { ... }":
            {
              "id": "helm_release.cert_manager",
              "type": "helm_release",
              "name": "cert_manager",
              "provider": "helm",
              "properties": {
                "chart": "cert-manager",
                "namespace": "cert-manager"
              }
            }

            OUTPUT FORMAT (JSON only, no markdown):
            {
              "resources": [
                {
                  "id": "exact_terraform_identifier",
                  "type": "terraform_resource_type", 
                  "name": "resource_name",
                  "provider": "aws|kubernetes|helm|etc",
                  "properties": {
                    "key_properties": "extracted_from_terraform"
                  }
                }
              ],
              "relationships": [
                {
                  "source": "exact_terraform_identifier",
                  "target": "exact_terraform_identifier",
                  "type": "RELATIONSHIP_TYPE",
                  "description": "detailed_technical_description",
                  "confidence": 0.95
                }
              ]
            }

            CRITICAL REQUIREMENTS:
            - Extract EVERY resource definition from the Terraform configuration
            - Map ALL possible relationships between resources
            - Use exact resource identifiers as they appear in the configuration
            - Include both explicit (depends_on) and implicit relationships
            - Provide detailed technical descriptions explaining HOW resources interact
            - Set confidence levels: 0.9-1.0 for explicit references, 0.8-0.9 for implicit dependencies
            - Focus on infrastructure connectivity, dependencies, and data flow
            - Identify cross-provider relationships (AWS → Kubernetes → Helm)
            """, contextInfo.toString(), mergedTerraformText.getMergedContent());
    }



    /**
     * Calls the LLM API with the analysis prompt.
     * Supports both OpenAI and Claude API formats.
     */
    private String callLLMAPI(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("LLM API key not configured, returning mock response");
            return getMockLLMResponse();
        }

        try {
            // Determine API type based on URL
            boolean isClaudeAPI = apiUrl.contains("anthropic.com");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();

            if (isClaudeAPI) {
                // Claude API format
                headers.set("x-api-key", apiKey);
                headers.set("anthropic-version", "2023-06-01");

                requestBody.put("model", model);
                requestBody.put("max_tokens", maxTokens);
                requestBody.put("temperature", temperature);
                requestBody.put("messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ));

            } else {
                // OpenAI API format
                headers.setBearerAuth(apiKey);

                requestBody.put("model", model);
                requestBody.put("messages", List.of(
                        Map.of("role", "system", "content", "You are an expert Terraform infrastructure analyst. Analyze Terraform configurations and extract resources and relationships with precise technical accuracy. Always respond with valid JSON only."),
                        Map.of("role", "user", "content", prompt)
                ));
                requestBody.put("max_tokens", maxTokens);
                requestBody.put("temperature", temperature);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                if (isClaudeAPI) {
                    // Claude response format: {"content": [{"text": "response"}]}
                    List<Map<String, Object>> content = (List<Map<String, Object>>) responseBody.get("content");
                    if (content != null && !content.isEmpty()) {
                        return (String) content.get(0).get("text");
                    }
                } else {
                    // OpenAI response format: {"choices": [{"message": {"content": "response"}}]}
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        return (String) message.get("content");
                    }
                }
            }

            logger.warn("Unexpected LLM API response, using mock response");
            return getMockLLMResponse();

        } catch (Exception e) {
            logger.error("Error calling LLM API", e);
            return getMockLLMResponse();
        }
    }

    /**
     * Provides a mock LLM response for testing when API is not available.
     */
    private String getMockLLMResponse() {
        return """
            {
              "resources": [
                {
                  "id": "aws_instance.web",
                  "type": "aws_instance",
                  "name": "web",
                  "provider": "aws",
                  "properties": {
                    "instance_type": "t3.micro",
                    "ami": "ami-12345678"
                  }
                },
                {
                  "id": "aws_db_instance.database",
                  "type": "aws_db_instance",
                  "name": "database",
                  "provider": "aws",
                  "properties": {
                    "engine": "mysql",
                    "instance_class": "db.t3.micro"
                  }
                }
              ],
              "relationships": [
                {
                  "source": "aws_instance.web",
                  "target": "aws_db_instance.database",
                  "type": "CONNECTS_TO",
                  "description": "Web server connects to database for data persistence",
                  "confidence": 0.85
                }
              ]
            }
            """;
    }

    /**
     * Parses the LLM response into a structured result object.
     */
    private LLMAnalysisResult parseLLMResponse(String llmResponse) {
        try {
            // Clean up the response (remove markdown code blocks if present)
            String cleanResponse = llmResponse.trim();
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }

            Map<String, Object> responseMap = objectMapper.readValue(cleanResponse, Map.class);
            return LLMAnalysisResult.fromMap(responseMap);

        } catch (Exception e) {
            logger.error("Error parsing LLM response", e);
            return new LLMAnalysisResult();
        }
    }

    /**
     * Result class for LLM analysis.
     */
    public static class LLMAnalysisResult {
        private List<LLMResource> resources = List.of();
        private List<LLMRelationship> relationships = List.of();

        public static LLMAnalysisResult fromMap(Map<String, Object> map) {
            LLMAnalysisResult result = new LLMAnalysisResult();

            // Parse resources with validation
            if (map.containsKey("resources")) {
                try {
                    List<Map<String, Object>> resourceMaps = (List<Map<String, Object>>) map.get("resources");
                    result.resources = resourceMaps.stream()
                            .map(resourceMap -> {
                                try {
                                    return LLMResource.fromMap(resourceMap);
                                } catch (Exception e) {
                                    logger.warn("Failed to parse resource: {}", e.getMessage());
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .toList();
                } catch (Exception e) {
                    logger.error("Error parsing resources from LLM response", e);
                    result.resources = List.of();
                }
            }

            // Parse relationships with validation
            if (map.containsKey("relationships")) {
                try {
                    List<Map<String, Object>> relationshipMaps = (List<Map<String, Object>>) map.get("relationships");
                    result.relationships = relationshipMaps.stream()
                            .map(relationshipMap -> {
                                try {
                                    return LLMRelationship.fromMap(relationshipMap);
                                } catch (Exception e) {
                                    logger.warn("Failed to parse relationship: {}", e.getMessage());
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .toList();
                } catch (Exception e) {
                    logger.error("Error parsing relationships from LLM response", e);
                    result.relationships = List.of();
                }
            }

            return result;
        }

        // Getters
        public List<LLMResource> getResources() { return resources; }
        public List<LLMRelationship> getRelationships() { return relationships; }
    }


}