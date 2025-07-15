package com.terraform.neo4j.service;

import com.terraform.neo4j.model.TerraformFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FileInputHandlerTest {

    private FileInputHandler fileInputHandler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileInputHandler = new FileInputHandler();
    }

    @Test
    void shouldReadTerraformFilesFromDirectory() throws IOException {
        // Given
        Path tfFile1 = tempDir.resolve("main.tf");
        Path tfFile2 = tempDir.resolve("variables.tf");
        Path nonTfFile = tempDir.resolve("readme.txt");
        
        Files.writeString(tfFile1, "resource \"aws_instance\" \"example\" {\n  ami = \"ami-12345\"\n}");
        Files.writeString(tfFile2, "variable \"instance_type\" {\n  default = \"t2.micro\"\n}");
        Files.writeString(nonTfFile, "This is not a terraform file");

        // When
        List<TerraformFile> result = fileInputHandler.readTerraformFiles(tempDir.toString());

        // Then
        assertEquals(2, result.size());
        
        TerraformFile mainTf = result.stream()
                .filter(tf -> tf.getFileName().equals("main.tf"))
                .findFirst()
                .orElseThrow();
        
        assertEquals("main.tf", mainTf.getFileName());
        assertTrue(mainTf.getContent().contains("aws_instance"));
        assertTrue(mainTf.getFilePath().endsWith("main.tf"));

        TerraformFile variablesTf = result.stream()
                .filter(tf -> tf.getFileName().equals("variables.tf"))
                .findFirst()
                .orElseThrow();
        
        assertEquals("variables.tf", variablesTf.getFileName());
        assertTrue(variablesTf.getContent().contains("variable"));
    }

    @Test
    void shouldReadTerraformFilesFromNestedDirectories() throws IOException {
        // Given
        Path subDir = tempDir.resolve("modules").resolve("vpc");
        Files.createDirectories(subDir);
        
        Path rootTf = tempDir.resolve("main.tf");
        Path nestedTf = subDir.resolve("vpc.tf");
        
        Files.writeString(rootTf, "module \"vpc\" {\n  source = \"./modules/vpc\"\n}");
        Files.writeString(nestedTf, "resource \"aws_vpc\" \"main\" {\n  cidr_block = \"10.0.0.0/16\"\n}");

        // When
        List<TerraformFile> result = fileInputHandler.readTerraformFiles(tempDir.toString());

        // Then
        assertEquals(2, result.size());
        
        assertTrue(result.stream().anyMatch(tf -> tf.getFileName().equals("main.tf")));
        assertTrue(result.stream().anyMatch(tf -> tf.getFileName().equals("vpc.tf")));
    }

    @Test
    void shouldReadTerraformFilesFromZipFile() throws IOException {
        // Given
        Path zipFile = tempDir.resolve("terraform.zip");
        createZipFileWithTerraformFiles(zipFile);

        // When
        List<TerraformFile> result = fileInputHandler.readTerraformFiles(zipFile.toString());

        // Then
        assertEquals(2, result.size());
        
        TerraformFile mainTf = result.stream()
                .filter(tf -> tf.getFileName().equals("main.tf"))
                .findFirst()
                .orElseThrow();
        
        assertEquals("main.tf", mainTf.getFileName());
        assertEquals("main.tf", mainTf.getFilePath());
        assertTrue(mainTf.getContent().contains("aws_instance"));

        TerraformFile modulesTf = result.stream()
                .filter(tf -> tf.getFileName().equals("modules.tf"))
                .findFirst()
                .orElseThrow();
        
        assertEquals("modules.tf", modulesTf.getFileName());
        assertEquals("modules/modules.tf", modulesTf.getFilePath());
        assertTrue(modulesTf.getContent().contains("aws_vpc"));
    }

    @Test
    void shouldIgnoreNonTerraformFilesInZip() throws IOException {
        // Given
        Path zipFile = tempDir.resolve("mixed.zip");
        createZipFileWithMixedFiles(zipFile);

        // When
        List<TerraformFile> result = fileInputHandler.readTerraformFiles(zipFile.toString());

        // Then
        assertEquals(1, result.size());
        assertEquals("infrastructure.tf", result.get(0).getFileName());
    }

    @Test
    void shouldReturnEmptyListForDirectoryWithoutTerraformFiles() throws IOException {
        // Given
        Path nonTfFile = tempDir.resolve("readme.txt");
        Files.writeString(nonTfFile, "No terraform files here");

        // When
        List<TerraformFile> result = fileInputHandler.readTerraformFiles(tempDir.toString());

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForZipWithoutTerraformFiles() throws IOException {
        // Given
        Path zipFile = tempDir.resolve("empty.zip");
        createZipFileWithoutTerraformFiles(zipFile);

        // When
        List<TerraformFile> result = fileInputHandler.readTerraformFiles(zipFile.toString());

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionForNullInputPath() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fileInputHandler.readTerraformFiles(null)
        );
        
        assertEquals("Input path cannot be null or empty", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionForEmptyInputPath() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fileInputHandler.readTerraformFiles("")
        );
        
        assertEquals("Input path cannot be null or empty", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionForNonExistentPath() {
        // Given
        String nonExistentPath = tempDir.resolve("non-existent").toString();

        // When & Then
        FileNotFoundException exception = assertThrows(
                FileNotFoundException.class,
                () -> fileInputHandler.readTerraformFiles(nonExistentPath)
        );
        
        assertTrue(exception.getMessage().contains("Path does not exist"));
    }

    @Test
    void shouldThrowExceptionForRegularFileInput() throws IOException {
        // Given
        Path regularFile = tempDir.resolve("regular.txt");
        Files.writeString(regularFile, "This is a regular file");

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fileInputHandler.readTerraformFiles(regularFile.toString())
        );
        
        assertTrue(exception.getMessage().contains("Input path must be either a directory or a zip file"));
    }

    @Test
    void shouldHandleCaseInsensitiveTerraformExtension() throws IOException {
        // Given
        Path tfFile1 = tempDir.resolve("main.TF");
        Path tfFile2 = tempDir.resolve("variables.Tf");
        
        Files.writeString(tfFile1, "resource \"aws_instance\" \"example\" {}");
        Files.writeString(tfFile2, "variable \"test\" {}");

        // When
        List<TerraformFile> result = fileInputHandler.readTerraformFiles(tempDir.toString());

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void shouldValidateContainsTerraformFiles() throws IOException {
        // Given
        Path tfFile = tempDir.resolve("main.tf");
        Files.writeString(tfFile, "resource \"aws_instance\" \"example\" {}");

        // When
        boolean containsTf = fileInputHandler.containsTerraformFiles(tempDir.toString());

        // Then
        assertTrue(containsTf);
    }

    @Test
    void shouldReturnFalseWhenNoTerraformFilesFound() throws IOException {
        // Given
        Path nonTfFile = tempDir.resolve("readme.txt");
        Files.writeString(nonTfFile, "No terraform files");

        // When
        boolean containsTf = fileInputHandler.containsTerraformFiles(tempDir.toString());

        // Then
        assertFalse(containsTf);
    }

    @Test
    void shouldHandleEmptyTerraformFile() throws IOException {
        // Given
        Path emptyTfFile = tempDir.resolve("empty.tf");
        Files.writeString(emptyTfFile, "");

        // When
        List<TerraformFile> result = fileInputHandler.readTerraformFiles(tempDir.toString());

        // Then
        assertEquals(1, result.size());
        assertEquals("", result.get(0).getContent());
        assertEquals("empty.tf", result.get(0).getFileName());
    }

    private void createZipFileWithTerraformFiles(Path zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add main.tf
            ZipEntry mainEntry = new ZipEntry("main.tf");
            zos.putNextEntry(mainEntry);
            zos.write("resource \"aws_instance\" \"web\" {\n  ami = \"ami-12345\"\n}".getBytes());
            zos.closeEntry();

            // Add nested terraform file
            ZipEntry moduleEntry = new ZipEntry("modules/modules.tf");
            zos.putNextEntry(moduleEntry);
            zos.write("resource \"aws_vpc\" \"main\" {\n  cidr_block = \"10.0.0.0/16\"\n}".getBytes());
            zos.closeEntry();
        }
    }

    private void createZipFileWithMixedFiles(Path zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add terraform file
            ZipEntry tfEntry = new ZipEntry("infrastructure.tf");
            zos.putNextEntry(tfEntry);
            zos.write("resource \"aws_s3_bucket\" \"example\" {}".getBytes());
            zos.closeEntry();

            // Add non-terraform file
            ZipEntry txtEntry = new ZipEntry("readme.txt");
            zos.putNextEntry(txtEntry);
            zos.write("This is a readme file".getBytes());
            zos.closeEntry();

            // Add another non-terraform file
            ZipEntry jsonEntry = new ZipEntry("config.json");
            zos.putNextEntry(jsonEntry);
            zos.write("{\"key\": \"value\"}".getBytes());
            zos.closeEntry();
        }
    }

    private void createZipFileWithoutTerraformFiles(Path zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add only non-terraform files
            ZipEntry txtEntry = new ZipEntry("readme.txt");
            zos.putNextEntry(txtEntry);
            zos.write("No terraform files here".getBytes());
            zos.closeEntry();

            ZipEntry jsonEntry = new ZipEntry("config.json");
            zos.putNextEntry(jsonEntry);
            zos.write("{\"config\": \"value\"}".getBytes());
            zos.closeEntry();
        }
    }
}