package com.terraform.neo4j.service;

import com.terraform.neo4j.model.TerraformFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for handling file input operations including reading Terraform files
 * from directories and extracting them from zip files.
 */
@Service
public class FileInputHandler {

    private static final Logger logger = LoggerFactory.getLogger(FileInputHandler.class);
    private static final String TERRAFORM_FILE_EXTENSION = ".tf";

    /**
     * Reads Terraform files from the specified input path.
     * Supports both directory paths and zip file paths.
     *
     * @param inputPath Path to directory or zip file containing Terraform files
     * @return List of TerraformFile objects
     * @throws IOException if file reading fails
     */
    public List<TerraformFile> readTerraformFiles(String inputPath) throws IOException {
        if (inputPath == null || inputPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Input path cannot be null or empty");
        }

        Path path = Paths.get(inputPath);
        
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Path does not exist: " + inputPath);
        }

        if (inputPath.toLowerCase().endsWith(".zip")) {
            logger.info("Reading Terraform files from zip: {}", inputPath);
            return readFromZipFile(inputPath);
        } else if (Files.isDirectory(path)) {
            logger.info("Reading Terraform files from directory: {}", inputPath);
            return readFromDirectory(inputPath);
        } else {
            throw new IllegalArgumentException("Input path must be either a directory or a zip file: " + inputPath);
        }
    }

    /**
     * Recursively reads all .tf files from the specified directory.
     *
     * @param directoryPath Path to directory containing Terraform files
     * @return List of TerraformFile objects
     * @throws IOException if directory reading fails
     */
    private List<TerraformFile> readFromDirectory(String directoryPath) throws IOException {
        Path dirPath = Paths.get(directoryPath);
        
        if (!Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Path is not a directory: " + directoryPath);
        }

        List<TerraformFile> terraformFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(dirPath)) {
            List<Path> tfFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isTerraformFile)
                    .collect(Collectors.toList());

            for (Path tfFile : tfFiles) {
                try {
                    TerraformFile terraformFile = readTerraformFile(tfFile);
                    terraformFiles.add(terraformFile);
                    logger.debug("Read Terraform file: {}", tfFile.getFileName());
                } catch (IOException e) {
                    logger.warn("Failed to read Terraform file: {}", tfFile, e);
                    // Continue processing other files
                }
            }
        }

        logger.info("Successfully read {} Terraform files from directory", terraformFiles.size());
        return terraformFiles;
    }

    /**
     * Extracts and reads .tf files from the specified zip file.
     *
     * @param zipPath Path to zip file containing Terraform files
     * @return List of TerraformFile objects
     * @throws IOException if zip file reading fails
     */
    private List<TerraformFile> readFromZipFile(String zipPath) throws IOException {
        List<TerraformFile> terraformFiles = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && isTerraformFile(entry.getName())) {
                    try {
                        String content = readZipEntryContent(zis);
                        TerraformFile terraformFile = TerraformFile.builder()
                                .fileName(getFileNameFromPath(entry.getName()))
                                .filePath(entry.getName())
                                .content(content)
                                .build();
                        
                        terraformFiles.add(terraformFile);
                        logger.debug("Extracted Terraform file from zip: {}", entry.getName());
                    } catch (IOException e) {
                        logger.warn("Failed to read zip entry: {}", entry.getName(), e);
                        // Continue processing other entries
                    }
                }
                zis.closeEntry();
            }
        }

        logger.info("Successfully extracted {} Terraform files from zip", terraformFiles.size());
        return terraformFiles;
    }

    /**
     * Reads content from a zip entry.
     *
     * @param zis ZipInputStream positioned at the entry
     * @return Content of the zip entry as string
     * @throws IOException if reading fails
     */
    private String readZipEntryContent(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        
        while ((length = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, length);
        }
        
        return baos.toString("UTF-8");
    }

    /**
     * Reads a single Terraform file from the file system.
     *
     * @param filePath Path to the Terraform file
     * @return TerraformFile object
     * @throws IOException if file reading fails
     */
    private TerraformFile readTerraformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        
        return TerraformFile.builder()
                .fileName(filePath.getFileName().toString())
                .filePath(filePath.toString())
                .content(content)
                .build();
    }

    /**
     * Validates if a file path represents a Terraform file.
     *
     * @param path File path to validate
     * @return true if the file is a Terraform file
     */
    private boolean isTerraformFile(Path path) {
        return isTerraformFile(path.getFileName().toString());
    }

    /**
     * Validates if a file name represents a Terraform file.
     *
     * @param fileName File name to validate
     * @return true if the file is a Terraform file
     */
    private boolean isTerraformFile(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(TERRAFORM_FILE_EXTENSION);
    }

    /**
     * Extracts the file name from a full path.
     *
     * @param fullPath Full file path
     * @return File name only
     */
    private String getFileNameFromPath(String fullPath) {
        if (fullPath == null) {
            return null;
        }
        
        int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
    }

    /**
     * Validates if the provided path contains any Terraform files.
     *
     * @param inputPath Path to validate
     * @return true if Terraform files are found
     * @throws IOException if path validation fails
     */
    public boolean containsTerraformFiles(String inputPath) throws IOException {
        List<TerraformFile> files = readTerraformFiles(inputPath);
        return !files.isEmpty();
    }
}