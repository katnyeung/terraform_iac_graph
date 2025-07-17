package com.terraform.neo4j.controller;

import com.terraform.neo4j.dto.ErrorResponse;
import com.terraform.neo4j.dto.ParseRequest;
import com.terraform.neo4j.dto.ParseResponse;

import com.terraform.neo4j.model.TerraformFile;
import com.terraform.neo4j.model.MergedTerraformText;
import com.terraform.neo4j.service.FileInputHandler;
import com.terraform.neo4j.service.LLMInfrastructureAnalyzer;
import com.terraform.neo4j.service.TerraformTextMerger;
import com.terraform.neo4j.service.DirectNeo4jMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for Terraform parsing operations.
 * Provides endpoints for parsing Terraform configurations from text content or file uploads.
 */
@RestController
@RequestMapping("/api/terraform")
@Tag(name = "Terraform Parser", description = "APIs for parsing Terraform configurations and creating Neo4j graphs")
public class TerraformController {

    private static final Logger logger = LoggerFactory.getLogger(TerraformController.class);

    private final FileInputHandler fileInputHandler;
    private final TerraformTextMerger terraformTextMerger;
    private final LLMInfrastructureAnalyzer llmAnalyzer;
    private final DirectNeo4jMapper directNeo4jMapper;

    public TerraformController(FileInputHandler fileInputHandler,
                             TerraformTextMerger terraformTextMerger,
                             LLMInfrastructureAnalyzer llmAnalyzer,
                             DirectNeo4jMapper directNeo4jMapper) {
        this.fileInputHandler = fileInputHandler;
        this.terraformTextMerger = terraformTextMerger;
        this.llmAnalyzer = llmAnalyzer;
        this.directNeo4jMapper = directNeo4jMapper;
    }

    @Operation(
        summary = "Parse Terraform configuration from text content",
        description = "Parses Terraform configuration provided as text content and creates corresponding nodes in Neo4j graph database"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Terraform configuration parsed successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ParseResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request or malformed Terraform configuration",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Terraform parsing errors",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error during processing",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping(value = "/parse", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> parseTerraformContent(
            @Parameter(description = "Terraform configuration content to parse", required = true)
            @Valid @RequestBody ParseRequest request) {
        
        logger.info("Received request to parse Terraform content (length: {})", 
                   request.getContent() != null ? request.getContent().length() : 0);

        try {
            // Create a temporary TerraformFile from the content
            TerraformFile terraformFile = TerraformFile.builder()
                    .fileName("inline.tf")
                    .filePath("inline.tf")
                    .content(request.getContent())
                    .build();

            // NEW TEXT-BASED PIPELINE:

            // 1. Merge Terraform files into LLM-optimized text format
            MergedTerraformText mergedText = terraformTextMerger.mergeTerraformFiles(List.of(terraformFile));
            logger.info("Text merging completed: {} characters, {} resources identified", 
                       mergedText.getContentLength(), mergedText.getTotalResources());

            // 2. Analyze with enhanced LLM using direct text processing
            LLMInfrastructureAnalyzer.LLMAnalysisResult llmAnalysis = llmAnalyzer.analyzeInfrastructure(mergedText);
            logger.info("Enhanced LLM analysis completed with {} resources and {} relationships",
                       llmAnalysis.getResources().size(), llmAnalysis.getRelationships().size());

            // 3. Create Neo4j graph directly from LLM analysis
            directNeo4jMapper.createGraphFromLLMAnalysis(llmAnalysis);

            // Build enhanced response with new pipeline results
            ParseResponse response = buildEnhancedParseResponseFromLLM(mergedText, llmAnalysis);
            response.setMessage(String.format("Successfully processed Terraform content using direct text analysis: %d resources with %d relationships mapped to Neo4j graph",
                                            llmAnalysis.getResources().size(), llmAnalysis.getRelationships().size()));

            logger.info("Successfully processed Terraform content with new pipeline: {} resources, {} relationships", 
                       llmAnalysis.getResources().size(), llmAnalysis.getRelationships().size());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.badRequest("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error processing Terraform content", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.internalServerError(
                            "Error processing Terraform configuration",
                            e.getMessage()));
        }
    }

    @Operation(
        summary = "Parse Terraform configuration from zip file upload",
        description = "Parses Terraform configuration files from an uploaded zip file and creates corresponding nodes in Neo4j graph database"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Terraform files parsed successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ParseResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid file upload or no Terraform files found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Terraform parsing errors",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error during processing",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @PostMapping(value = "/parse-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> parseTerraformZip(
            @Parameter(description = "Zip file containing Terraform configuration files", required = true)
            @RequestParam("file") MultipartFile file) {
        
        logger.info("Received request to parse Terraform zip file: {} (size: {} bytes)", 
                   file.getOriginalFilename(), file.getSize());

        // Validate file upload
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.badRequest("Uploaded file is empty"));
        }

        if (!isZipFile(file)) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.badRequest("Uploaded file must be a zip file"));
        }

        Path tempFile = null;
        try {
            // Save uploaded file to temporary location
            tempFile = Files.createTempFile("terraform-upload-", ".zip");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            logger.debug("Saved uploaded file to temporary location: {}", tempFile);

            // Read Terraform files from zip
            List<TerraformFile> terraformFiles = fileInputHandler.readTerraformFiles(tempFile.toString());

            if (terraformFiles.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ErrorResponse.badRequest("No Terraform files (.tf) found in the uploaded zip"));
            }

            logger.info("Found {} Terraform files in uploaded zip", terraformFiles.size());

            // NEW TEXT-BASED PIPELINE (same as parse endpoint):

            // 1. Merge Terraform files into LLM-optimized text format
            MergedTerraformText mergedText = terraformTextMerger.mergeTerraformFiles(terraformFiles);
            logger.info("Text merging completed: {} characters, {} resources identified", 
                       mergedText.getContentLength(), mergedText.getTotalResources());

            // 2. Analyze with enhanced LLM using direct text processing
            LLMInfrastructureAnalyzer.LLMAnalysisResult llmAnalysis = llmAnalyzer.analyzeInfrastructure(mergedText);
            logger.info("Enhanced LLM analysis completed with {} resources and {} relationships",
                       llmAnalysis.getResources().size(), llmAnalysis.getRelationships().size());

            // 3. Create Neo4j graph directly from LLM analysis
            directNeo4jMapper.createGraphFromLLMAnalysis(llmAnalysis);

            // Build enhanced response with new pipeline results
            ParseResponse response = buildEnhancedParseResponseFromLLM(mergedText, llmAnalysis);
            response.setMessage(String.format("Successfully processed %d Terraform files using direct text analysis: %d resources with %d relationships mapped to Neo4j graph",
                                            terraformFiles.size(), llmAnalysis.getResources().size(), llmAnalysis.getRelationships().size()));

            logger.info("Successfully processed Terraform zip with new pipeline: {} files, {} resources, {} relationships", 
                       terraformFiles.size(), llmAnalysis.getResources().size(), llmAnalysis.getRelationships().size());
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Error reading uploaded zip file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.internalServerError(
                            "Error reading uploaded zip file",
                            e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid zip file content: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.badRequest("Invalid zip file: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error processing Terraform zip file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.internalServerError(
                            "Error processing Terraform zip file",
                            e.getMessage()));
        } finally {
            // Clean up temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    logger.debug("Cleaned up temporary file: {}", tempFile);
                } catch (IOException e) {
                    logger.warn("Failed to clean up temporary file: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Builds an enhanced ParseResponse from LLM analysis results using the new text-based pipeline.
     * Extracts resource counts and provider information from LLM results and maintains existing response structure.
     *
     * @param mergedText Merged Terraform text with metadata
     * @param llmAnalysis LLM analysis results with resources and relationships
     * @return Enhanced ParseResponse with new pipeline metrics
     */
    private ParseResponse buildEnhancedParseResponseFromLLM(MergedTerraformText mergedText,
                                                           LLMInfrastructureAnalyzer.LLMAnalysisResult llmAnalysis) {
        ParseResponse response = ParseResponse.success("Direct text analysis completed successfully", 
                                                      llmAnalysis.getResources().size());

        // Extract resource counts and provider information from LLM results
        Map<String, Long> resourceTypeCounts = llmAnalysis.getResources().stream()
                .collect(Collectors.groupingBy(resource -> resource.getType() != null ? resource.getType() : "unknown", 
                                             Collectors.counting()));

        resourceTypeCounts.forEach((type, count) ->
                response.addResourceTypeSummary(type, count.intValue()));

        // Collect unique providers from LLM analysis
        List<String> providers = llmAnalysis.getResources().stream()
                .map(resource -> resource.getProvider())
                .distinct()
                .filter(provider -> provider != null && !"unknown".equals(provider) && !provider.trim().isEmpty())
                .collect(Collectors.toList());
        response.setProvidersDetected(providers);

        // Categorize resources by type for identity vs regular classification
        long identityResourceCount = llmAnalysis.getResources().stream()
                .filter(resource -> isIdentityResource(resource.getType()))
                .count();
        long regularResourceCount = llmAnalysis.getResources().size() - identityResourceCount;

        response.setIdentityResourceCount((int) identityResourceCount);
        response.setRegularResourceCount((int) regularResourceCount);

        // Add relationship information
        response.addProperty("relationshipsFound", llmAnalysis.getRelationships().size());
        
        // Add relationship type breakdown
        Map<String, Long> relationshipTypeCounts = llmAnalysis.getRelationships().stream()
                .collect(Collectors.groupingBy(rel -> rel.getType() != null ? rel.getType() : "UNKNOWN", 
                                             Collectors.counting()));
        response.addProperty("relationshipTypes", relationshipTypeCounts);

        // Add new metrics for text processing performance
        response.addProperty("filesProcessed", mergedText.getFileNames().size());
        response.addProperty("contentLength", mergedText.getContentLength());
        response.addProperty("textMergingMode", mergedText.getMetadata("fallback_mode") != null ? "fallback" : "intelligent");
        response.addProperty("providersFromText", mergedText.getProviders().size());
        response.addProperty("logicalGroups", mergedText.getResourceGroups() != null ? 
                           mergedText.getResourceGroups().getTotalGroups() : 0);
        
        // Add processing statistics
        response.addProperty("totalResourcesFromText", mergedText.getTotalResources());
        response.addProperty("averageConfidence", calculateAverageConfidence(llmAnalysis.getRelationships()));
        response.addProperty("highConfidenceRelationships", countHighConfidenceRelationships(llmAnalysis.getRelationships()));
        
        // Add metadata about text merging quality
        if (mergedText.getMetadata() != null) {
            response.addProperty("textMergingMetadata", mergedText.getMetadata());
        }

        return response;
    }

    /**
     * Determines if a resource type is an identity-related resource.
     * Used for maintaining compatibility with existing response structure.
     */
    private boolean isIdentityResource(String resourceType) {
        if (resourceType == null) return false;
        
        String lowerType = resourceType.toLowerCase();
        return lowerType.contains("iam") || 
               lowerType.contains("role") || 
               lowerType.contains("policy") || 
               lowerType.contains("user") ||
               lowerType.contains("group") ||
               lowerType.contains("permission") ||
               lowerType.contains("identity");
    }

    /**
     * Calculates the average confidence score of relationships.
     */
    private double calculateAverageConfidence(List<com.terraform.neo4j.model.LLMRelationship> relationships) {
        if (relationships.isEmpty()) return 0.0;
        
        return relationships.stream()
                .mapToDouble(rel -> rel.getConfidence())
                .average()
                .orElse(0.0);
    }

    /**
     * Counts relationships with high confidence (>= 0.8).
     */
    private long countHighConfidenceRelationships(List<com.terraform.neo4j.model.LLMRelationship> relationships) {
        return relationships.stream()
                .filter(rel -> rel.getConfidence() >= 0.8)
                .count();
    }



    /**
     * Validates if the uploaded file is a zip file based on content type and filename.
     *
     * @param file Uploaded file
     * @return true if the file appears to be a zip file
     */
    private boolean isZipFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        // Check content type
        if (contentType != null && (contentType.equals("application/zip") || 
                                   contentType.equals("application/x-zip-compressed"))) {
            return true;
        }

        // Check file extension as fallback
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            return true;
        }

        return false;
    }
}