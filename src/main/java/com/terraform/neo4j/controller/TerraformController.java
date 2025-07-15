package com.terraform.neo4j.controller;

import com.terraform.neo4j.dto.ErrorResponse;
import com.terraform.neo4j.dto.ParseRequest;
import com.terraform.neo4j.dto.ParseResponse;
import com.terraform.neo4j.model.InfrastructureComponent;
import com.terraform.neo4j.model.IdentityType;
import com.terraform.neo4j.model.ParsedTerraform;
import com.terraform.neo4j.model.TerraformFile;
import com.terraform.neo4j.service.BasicResourceExtractor;
import com.terraform.neo4j.service.FileInputHandler;
import com.terraform.neo4j.service.SimpleNeo4jMapper;
import com.terraform.neo4j.service.TerraformParser;
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
    private final TerraformParser terraformParser;
    private final BasicResourceExtractor resourceExtractor;
    private final SimpleNeo4jMapper neo4jMapper;

    public TerraformController(FileInputHandler fileInputHandler,
                             TerraformParser terraformParser,
                             BasicResourceExtractor resourceExtractor,
                             SimpleNeo4jMapper neo4jMapper) {
        this.fileInputHandler = fileInputHandler;
        this.terraformParser = terraformParser;
        this.resourceExtractor = resourceExtractor;
        this.neo4jMapper = neo4jMapper;
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

            // Parse the Terraform content
            ParsedTerraform parsed = terraformParser.parse(List.of(terraformFile));

            // Check for parsing errors
            if (parsed.hasParseErrors()) {
                logger.warn("Terraform parsing completed with {} errors", parsed.getParseErrors().size());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(ErrorResponse.parsingError(
                                "Terraform configuration contains parsing errors",
                                parsed.getParseErrors()));
            }

            // Extract infrastructure components
            List<InfrastructureComponent> components = resourceExtractor.extractComponents(parsed);

            // Map to Neo4j graph
            neo4jMapper.mapToGraph(components);

            // Build response
            ParseResponse response = buildParseResponse(components, parsed);
            response.setMessage("Successfully parsed and mapped " + components.size() + " resources to Neo4j graph");

            logger.info("Successfully processed Terraform content: {} resources mapped to graph", components.size());
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

            // Parse the Terraform files
            ParsedTerraform parsed = terraformParser.parse(terraformFiles);

            // Check for parsing errors
            if (parsed.hasParseErrors()) {
                logger.warn("Terraform parsing completed with {} errors", parsed.getParseErrors().size());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(ErrorResponse.parsingError(
                                "Terraform configuration contains parsing errors",
                                parsed.getParseErrors()));
            }

            // Extract infrastructure components
            List<InfrastructureComponent> components = resourceExtractor.extractComponents(parsed);

            // Map to Neo4j graph
            neo4jMapper.mapToGraph(components);

            // Build response
            ParseResponse response = buildParseResponse(components, parsed);
            response.setMessage(String.format("Successfully parsed %d Terraform files and mapped %d resources to Neo4j graph", 
                                            terraformFiles.size(), components.size()));

            logger.info("Successfully processed Terraform zip: {} files, {} resources mapped to graph", 
                       terraformFiles.size(), components.size());
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
     * Builds a comprehensive ParseResponse from the processed components and parsed Terraform.
     *
     * @param components List of infrastructure components
     * @param parsed Parsed Terraform configuration
     * @return ParseResponse with detailed information
     */
    private ParseResponse buildParseResponse(List<InfrastructureComponent> components, ParsedTerraform parsed) {
        ParseResponse response = ParseResponse.success("Parsing completed successfully", components.size());

        // Count identity vs regular resources
        long identityCount = components.stream()
                .filter(c -> c.getIdentityType() == IdentityType.IDENTITY_RESOURCE)
                .count();
        long regularCount = components.size() - identityCount;

        response.setIdentityResourceCount((int) identityCount);
        response.setRegularResourceCount((int) regularCount);

        // Collect unique providers
        List<String> providers = components.stream()
                .map(InfrastructureComponent::getProvider)
                .distinct()
                .filter(provider -> !"UNKNOWN".equals(provider))
                .collect(Collectors.toList());
        response.setProvidersDetected(providers);

        // Create resource type summary
        Map<String, Long> resourceTypeCounts = components.stream()
                .collect(Collectors.groupingBy(InfrastructureComponent::getType, Collectors.counting()));

        resourceTypeCounts.forEach((type, count) -> 
                response.addResourceTypeSummary(type, count.intValue()));

        // Add any parsing errors (though we typically don't reach here if there are errors)
        if (parsed.hasParseErrors()) {
            response.setErrors(parsed.getParseErrors());
        }

        return response;
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