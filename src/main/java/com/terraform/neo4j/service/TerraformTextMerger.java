package com.terraform.neo4j.service;

import com.terraform.neo4j.component.*;
import com.terraform.neo4j.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for intelligently merging Terraform files into LLM-optimized text format.
 * Orchestrates the entire text processing pipeline from raw files to formatted output.
 */
@Service
public class TerraformTextMerger {
    
    private static final Logger logger = LoggerFactory.getLogger(TerraformTextMerger.class);
    
    private final FileScanner fileScanner;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final LogicalGrouper logicalGrouper;
    private final ContextInjector contextInjector;
    private final TextFormatter textFormatter;
    
    @Autowired
    public TerraformTextMerger(FileScanner fileScanner,
                              DependencyAnalyzer dependencyAnalyzer,
                              LogicalGrouper logicalGrouper,
                              ContextInjector contextInjector,
                              TextFormatter textFormatter) {
        this.fileScanner = fileScanner;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.logicalGrouper = logicalGrouper;
        this.contextInjector = contextInjector;
        this.textFormatter = textFormatter;
    }
    
    /**
     * Main method to merge Terraform files into LLM-optimized text format
     * 
     * @param files List of Terraform files to merge
     * @return MergedTerraformText optimized for LLM analysis
     */
    public MergedTerraformText mergeTerraformFiles(List<TerraformFile> files) {
        logger.info("Starting Terraform text merging pipeline for {} files", files.size());
        
        try {
            // Validate input
            if (files == null || files.isEmpty()) {
                throw new IllegalArgumentException("No Terraform files provided for merging");
            }
            
            // Log file summary
            logFileSummary(files);
            
            // Step 1: Scan files for structure analysis
            logger.debug("Step 1: Analyzing file structure");
            FileStructure structure = fileScanner.analyzeFiles(files);
            logger.info("File analysis complete - {} resources, {} variables, {} outputs across {} providers",
                structure.getTotalResources(), structure.getTotalVariables(), 
                structure.getTotalOutputs(), structure.getProviders().size());
            
            // Step 2: Build dependency map
            logger.debug("Step 2: Building dependency map");
            DependencyMap dependencies = dependencyAnalyzer.buildDependencyMap(files);
            logger.info("Dependency analysis complete - {} total dependencies identified", 
                dependencies.getTotalDependencies());
            
            // Step 3: Group resources logically
            logger.debug("Step 3: Creating logical resource groups");
            LogicalGroups groups = logicalGrouper.groupResources(structure, dependencies);
            logger.info("Logical grouping complete - {} groups created for {} resources", 
                groups.getTotalGroups(), groups.getTotalResources());
            
            // Step 4: Inject helpful context
            logger.debug("Step 4: Injecting contextual information");
            ContextualizedContent content = contextInjector.addContext(groups, dependencies, files);
            logger.info("Context injection complete - {} cross-references, {} relationship hints", 
                content.getTotalCrossReferences(), content.getTotalRelationshipHints());
            
            // Step 5: Format for LLM readability
            logger.debug("Step 5: Formatting content for LLM analysis");
            MergedTerraformText mergedText = textFormatter.formatForLLM(content);
            logger.info("Text formatting complete - {} characters, ready for LLM analysis", 
                mergedText.getContentLength());
            
            // Log final summary
            logMergingSummary(mergedText);
            
            return mergedText;
            
        } catch (Exception e) {
            logger.error("Error during Terraform text merging: {}", e.getMessage(), e);
            
            // Create fallback merged text
            return createFallbackMergedText(files, e);
        }
    }
    
    /**
     * Log summary of input files
     */
    private void logFileSummary(List<TerraformFile> files) {
        int totalContentLength = files.stream()
                .mapToInt(file -> file.getContent() != null ? file.getContent().length() : 0)
                .sum();
        
        logger.info("Input files summary:");
        logger.info("  - File count: {}", files.size());
        logger.info("  - Total content length: {} characters", totalContentLength);
        logger.info("  - Average file size: {} characters", 
            files.isEmpty() ? 0 : totalContentLength / files.size());
        
        // Log individual file sizes for debugging
        if (logger.isDebugEnabled()) {
            for (TerraformFile file : files) {
                int contentLength = file.getContent() != null ? file.getContent().length() : 0;
                logger.debug("  - {}: {} characters", file.getFileName(), contentLength);
            }
        }
    }
    
    /**
     * Log summary of merging results
     */
    private void logMergingSummary(MergedTerraformText mergedText) {
        logger.info("Terraform text merging completed successfully:");
        logger.info("  - Final content length: {} characters", mergedText.getContentLength());
        logger.info("  - Files processed: {}", mergedText.getFileNames().size());
        logger.info("  - Resources identified: {}", mergedText.getTotalResources());
        logger.info("  - Providers detected: {}", mergedText.getProviders().size());
        logger.info("  - Logical groups: {}", mergedText.getResourceGroups().getTotalGroups());
        logger.info("  - Dependencies mapped: {}", mergedText.getCrossReferences().getTotalDependencies());
        logger.info("  - Ready for LLM: {}", mergedText.isReadyForLLM());
        
        // Log metadata
        if (mergedText.getMetadata() != null && !mergedText.getMetadata().isEmpty()) {
            logger.debug("Metadata: {}", mergedText.getMetadata());
        }
    }
    
    /**
     * Create a fallback merged text when the main pipeline fails
     */
    private MergedTerraformText createFallbackMergedText(List<TerraformFile> files, Exception error) {
        logger.warn("Creating fallback merged text due to error: {}", error.getMessage());
        
        try {
            // Create basic concatenation as fallback
            StringBuilder content = new StringBuilder();
            
            content.append("# TERRAFORM FILES - FALLBACK MODE\n");
            content.append("# Error occurred during intelligent merging: ").append(error.getMessage()).append("\n");
            content.append("# Falling back to basic file concatenation\n\n");
            content.append("=".repeat(80)).append("\n\n");
            
            for (TerraformFile file : files) {
                content.append("# FILE: ").append(file.getFileName()).append("\n");
                content.append("=".repeat(80)).append("\n");
                
                if (file.getContent() != null) {
                    content.append(file.getContent());
                } else {
                    content.append("# [No content available]");
                }
                
                if (!file.getContent().endsWith("\n")) {
                    content.append("\n");
                }
                content.append("\n").append("=".repeat(80)).append("\n\n");
            }
            
            return MergedTerraformText.builder()
                    .mergedContent(content.toString())
                    .fileNames(files.stream().map(TerraformFile::getFileName).toList())
                    .totalResources(0) // Unknown in fallback mode
                    .providers(java.util.Set.of()) // Unknown in fallback mode
                    .addMetadata("fallback_mode", true)
                    .addMetadata("error_message", error.getMessage())
                    .addMetadata("error_type", error.getClass().getSimpleName())
                    .addMetadata("fallback_timestamp", System.currentTimeMillis())
                    .build();
                    
        } catch (Exception fallbackError) {
            logger.error("Error even in fallback mode: {}", fallbackError.getMessage(), fallbackError);
            
            // Ultimate fallback - minimal content
            return MergedTerraformText.builder()
                    .mergedContent("# ERROR: Unable to merge Terraform files\n# " + error.getMessage())
                    .fileNames(files.stream().map(TerraformFile::getFileName).toList())
                    .totalResources(0)
                    .providers(java.util.Set.of())
                    .addMetadata("critical_error", true)
                    .addMetadata("error_message", error.getMessage())
                    .addMetadata("fallback_error", fallbackError.getMessage())
                    .build();
        }
    }
    
    /**
     * Validate that the merged text is suitable for LLM processing
     */
    public boolean validateMergedText(MergedTerraformText mergedText) {
        if (mergedText == null) {
            logger.error("Merged text is null");
            return false;
        }
        
        if (!mergedText.isReadyForLLM()) {
            logger.error("Merged text is not ready for LLM processing: {}", mergedText.getSummary());
            return false;
        }
        
        if (mergedText.getContentLength() == 0) {
            logger.error("Merged text has no content");
            return false;
        }
        
        if (mergedText.getTotalResources() == 0) {
            logger.warn("Merged text contains no resources - this may indicate parsing issues");
        }
        
        logger.debug("Merged text validation passed: {}", mergedText.getSummary());
        return true;
    }
    
    /**
     * Get processing statistics for monitoring
     */
    public ProcessingStats getLastProcessingStats(MergedTerraformText mergedText) {
        if (mergedText == null || mergedText.getMetadata() == null) {
            return new ProcessingStats();
        }
        
        ProcessingStats stats = new ProcessingStats();
        stats.setFilesProcessed(mergedText.getFileNames() != null ? mergedText.getFileNames().size() : 0);
        stats.setResourcesIdentified(mergedText.getTotalResources());
        stats.setProvidersDetected(mergedText.getProviders() != null ? mergedText.getProviders().size() : 0);
        stats.setContentLength(mergedText.getContentLength());
        stats.setFallbackMode((Boolean) mergedText.getMetadata("fallback_mode"));
        stats.setTruncated((Boolean) mergedText.getMetadata("truncated"));
        
        return stats;
    }
    
    /**
     * Statistics class for monitoring text merging performance
     */
    public static class ProcessingStats {
        private int filesProcessed;
        private int resourcesIdentified;
        private int providersDetected;
        private long contentLength;
        private Boolean fallbackMode;
        private Boolean truncated;
        
        // Getters and setters
        public int getFilesProcessed() { return filesProcessed; }
        public void setFilesProcessed(int filesProcessed) { this.filesProcessed = filesProcessed; }
        
        public int getResourcesIdentified() { return resourcesIdentified; }
        public void setResourcesIdentified(int resourcesIdentified) { this.resourcesIdentified = resourcesIdentified; }
        
        public int getProvidersDetected() { return providersDetected; }
        public void setProvidersDetected(int providersDetected) { this.providersDetected = providersDetected; }
        
        public long getContentLength() { return contentLength; }
        public void setContentLength(long contentLength) { this.contentLength = contentLength; }
        
        public Boolean getFallbackMode() { return fallbackMode; }
        public void setFallbackMode(Boolean fallbackMode) { this.fallbackMode = fallbackMode; }
        
        public Boolean getTruncated() { return truncated; }
        public void setTruncated(Boolean truncated) { this.truncated = truncated; }
        
        @Override
        public String toString() {
            return String.format("ProcessingStats[files=%d, resources=%d, providers=%d, length=%d, fallback=%s, truncated=%s]",
                filesProcessed, resourcesIdentified, providersDetected, contentLength, fallbackMode, truncated);
        }
    }
}