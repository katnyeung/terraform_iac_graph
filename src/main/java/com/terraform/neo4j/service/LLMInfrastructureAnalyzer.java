package com.terraform.neo4j.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that uses LLM to analyze Terraform infrastructure and generate
 * meaningful relationships and insights for Neo4j graph creation.
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

    public LLMInfrastructureAnalyzer() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyzes Terraform infrastructure using LLM to generate meaningful
     * relationships and graph structure.
     *
     * @param terraformSummary JSON summary of Terraform configuration
     * @return LLM analysis result with relationships
     */
    public LLMAnalysisResult analyzeInfrastructure(String terraformSummary) {
        logger.info("Starting LLM analysis of Terraform infrastructure");

        try {
            String prompt = buildAnalysisPrompt(terraformSummary);
            String llmResponse = callLLMAPI(prompt);

            logger.debug("LLM Response: {}", llmResponse);

            LLMAnalysisResult result = parseLLMResponse(llmResponse);
            logger.info("LLM analysis completed. Found {} relationships",
                    result.getRelationships().size());

            return result;

        } catch (Exception e) {
            logger.error("Error during LLM analysis", e);
            // Return empty result instead of failing completely
            return new LLMAnalysisResult();
        }
    }

    /**
     * Builds the analysis prompt for the LLM.
     */
    private String buildAnalysisPrompt(String terraformSummary) {
        return String.format("""
            You are an expert infrastructure architect specializing in detailed resource relationship mapping. Analyze the following Terraform configuration and map ALL possible relationships between resources with precise technical details.
            
            Terraform Configuration Summary:
            %s
            
            Your task is to create a comprehensive relationship map for Neo4j graph database. Focus ONLY on relationships - do NOT provide insights, summaries, or recommendations.
            
            Please provide your analysis in the following JSON format:
            {
              "relationships": [
                {
                  "source": "exact_resource_type.exact_resource_name",
                  "target": "exact_resource_type.exact_resource_name", 
                  "type": "CONNECTS_TO|DEPENDS_ON|MANAGES|ROUTES_TO|STORES_IN|AUTHENTICATES_WITH|PROVIDES_STORAGE_FOR|DEPLOYED_ON|PROTECTED_BY|USES|CONFIGURES|MOUNTS|ACCESSES",
                  "description": "Detailed technical description of how these resources interact",
                  "confidence": 0.95
                }
              ]
            }
            
            CRITICAL REQUIREMENTS:
            1. Map EVERY possible relationship between ALL resources mentioned in the configuration
            2. Include relationships for:
               - EKS cluster connections to Helm applications
               - EFS file system relationships to mount targets, security groups, and Kubernetes storage classes
               - Security group relationships to resources they protect
               - Helm application dependencies and interactions
               - Provider configurations and their target resources
               - Module outputs and the resources that consume them
            
            3. Use exact resource identifiers from the configuration (e.g., "aws_efs_file_system.ai_impact", "helm_release.cert_manager")
            4. For module references like "module.eks", map relationships to the actual resources that would be created by that module
            5. Include both explicit dependencies (depends_on) and implicit relationships (network access, storage mounting, etc.)
            6. Be specific about HOW resources interact (e.g., "EFS mount target provides NFS access point for EKS pods to mount persistent storage")
            
            RELATIONSHIP TYPES TO FOCUS ON:
            - DEPLOYED_ON: Helm releases deployed on EKS cluster
            - PROVIDES_STORAGE_FOR: EFS providing storage for EKS workloads
            - MANAGES: Storage classes managing EFS provisioning
            - PROTECTED_BY: Resources protected by security groups
            - MOUNTS: Mount targets providing access to file systems
            - ROUTES_TO: Ingress controllers routing traffic
            - DEPENDS_ON: Explicit and implicit dependencies
            - CONFIGURES: Configuration relationships
            - USES: Resource usage relationships
            
            Map relationships with confidence > 0.8. Provide detailed technical descriptions explaining the exact nature of each relationship.
            """, terraformSummary);
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
                requestBody.put("max_tokens", 2000);
                requestBody.put("messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ));

            } else {
                // OpenAI API format
                headers.setBearerAuth(apiKey);

                requestBody.put("model", model);
                requestBody.put("messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ));
                requestBody.put("max_tokens", 2000);
                requestBody.put("temperature", 0.3);
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
              "relationships": [
                {
                  "source": "aws_instance.web",
                  "target": "aws_db_instance.database",
                  "type": "CONNECTS_TO",
                  "description": "Web server connects to database for data persistence",
                  "confidence": 0.85
                }
              ],
              "insights": [
                {
                  "type": "ARCHITECTURE_PATTERN",
                  "title": "Two-tier Architecture",
                  "description": "Classic web server and database architecture pattern detected",
                  "severity": "LOW",
                  "resources": ["aws_instance.web", "aws_db_instance.database"]
                }
              ],
              "summary": {
                "architectureType": "monolith",
                "complexity": "low",
                "mainPurpose": "Simple web application with database backend"
              }
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
        private List<InfrastructureRelationship> relationships = List.of();

        public static LLMAnalysisResult fromMap(Map<String, Object> map) {
            LLMAnalysisResult result = new LLMAnalysisResult();

            // Parse relationships
            if (map.containsKey("relationships")) {
                List<Map<String, Object>> relationshipMaps = (List<Map<String, Object>>) map.get("relationships");
                result.relationships = relationshipMaps.stream()
                        .map(InfrastructureRelationship::fromMap)
                        .toList();
            }

            return result;
        }

        // Getters
        public List<InfrastructureRelationship> getRelationships() { return relationships; }

        // Dummy methods for backward compatibility (return empty collections)
        public List<InfrastructureInsight> getInsights() { return List.of(); }
        public ArchitectureSummary getSummary() { return new ArchitectureSummary(); }
    }

    /**
     * Represents a relationship between infrastructure resources.
     */
    public static class InfrastructureRelationship {
        private String source;
        private String target;
        private String type;
        private String description;
        private double confidence;

        public static InfrastructureRelationship fromMap(Map<String, Object> map) {
            InfrastructureRelationship rel = new InfrastructureRelationship();
            rel.source = (String) map.get("source");
            rel.target = (String) map.get("target");
            rel.type = (String) map.get("type");
            rel.description = (String) map.get("description");
            rel.confidence = map.containsKey("confidence") ?
                    ((Number) map.get("confidence")).doubleValue() : 0.5;
            return rel;
        }

        // Getters
        public String getSource() { return source; }
        public String getTarget() { return target; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public double getConfidence() { return confidence; }
    }

    /**
     * Represents an architectural insight or recommendation.
     */
    public static class InfrastructureInsight {
        private String type;
        private String title;
        private String description;
        private String severity;
        private List<String> resources;

        public static InfrastructureInsight fromMap(Map<String, Object> map) {
            InfrastructureInsight insight = new InfrastructureInsight();
            insight.type = (String) map.get("type");
            insight.title = (String) map.get("title");
            insight.description = (String) map.get("description");
            insight.severity = (String) map.get("severity");
            insight.resources = map.containsKey("resources") ?
                    (List<String>) map.get("resources") : List.of();
            return insight;
        }

        // Getters
        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getSeverity() { return severity; }
        public List<String> getResources() { return resources; }
    }

    /**
     * Summary of the overall architecture.
     */
    public static class ArchitectureSummary {
        private String architectureType;
        private String complexity;
        private String mainPurpose;

        public static ArchitectureSummary fromMap(Map<String, Object> map) {
            ArchitectureSummary summary = new ArchitectureSummary();
            summary.architectureType = (String) map.get("architectureType");
            summary.complexity = (String) map.get("complexity");
            summary.mainPurpose = (String) map.get("mainPurpose");
            return summary;
        }

        // Getters
        public String getArchitectureType() { return architectureType; }
        public String getComplexity() { return complexity; }
        public String getMainPurpose() { return mainPurpose; }
    }
}