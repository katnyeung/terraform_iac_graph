package com.terraform.neo4j.controller;

import com.terraform.neo4j.dto.ParseRequest;
import com.terraform.neo4j.dto.ParseResponse;
import com.terraform.neo4j.model.InfrastructureComponent;
import com.terraform.neo4j.model.IdentityType;
import com.terraform.neo4j.model.ParsedTerraform;
import com.terraform.neo4j.model.TerraformResource;
import com.terraform.neo4j.service.BasicResourceExtractor;
import com.terraform.neo4j.service.FileInputHandler;
import com.terraform.neo4j.service.SimpleNeo4jMapper;
import com.terraform.neo4j.service.TerraformParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TerraformController.
 */
@ExtendWith(MockitoExtension.class)
class TerraformControllerTest {

    @Mock
    private FileInputHandler fileInputHandler;

    @Mock
    private TerraformParser terraformParser;

    @Mock
    private BasicResourceExtractor resourceExtractor;

    @Mock
    private SimpleNeo4jMapper neo4jMapper;

    @InjectMocks
    private TerraformController terraformController;

    private ParseRequest validParseRequest;
    private ParsedTerraform mockParsedTerraform;
    private List<InfrastructureComponent> mockComponents;

    @BeforeEach
    void setUp() {
        // Setup valid parse request
        validParseRequest = new ParseRequest();
        validParseRequest.setContent("""
            resource "google_compute_instance" "vm" {
              name = "test-vm"
              machine_type = "e2-medium"
              service_account {
                email = "test@project.iam.gserviceaccount.com"
                scopes = ["cloud-platform"]
              }
            }
            
            resource "google_service_account" "sa" {
              account_id = "test-sa"
              display_name = "Test Service Account"
            }
            """);
        validParseRequest.setDescription("Test infrastructure");

        // Setup mock Terraform resources
        TerraformResource vmResource = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm")
                .arguments(Map.of(
                    "name", "test-vm",
                    "machine_type", "e2-medium"
                ))
                .build();

        TerraformResource saResource = TerraformResource.builder()
                .type("google_service_account")
                .name("sa")
                .arguments(Map.of(
                    "account_id", "test-sa",
                    "display_name", "Test Service Account"
                ))
                .build();

        // Setup mock parsed Terraform
        mockParsedTerraform = ParsedTerraform.builder()
                .resources(Arrays.asList(vmResource, saResource))
                .build();

        // Setup mock infrastructure components
        InfrastructureComponent vmComponent = InfrastructureComponent.builder()
                .id("google_compute_instance.vm.12345678")
                .type("google_compute_instance")
                .name("vm")
                .provider("GCP")
                .identityType(IdentityType.REGULAR_RESOURCE)
                .properties(Map.of("name", "test-vm", "machine_type", "e2-medium"))
                .build();

        InfrastructureComponent saComponent = InfrastructureComponent.builder()
                .id("google_service_account.sa.87654321")
                .type("google_service_account")
                .name("sa")
                .provider("GCP")
                .identityType(IdentityType.IDENTITY_RESOURCE)
                .properties(Map.of("account_id", "test-sa", "display_name", "Test Service Account"))
                .build();

        mockComponents = Arrays.asList(vmComponent, saComponent);
    }

    @Test
    void parseTerraformContent_ValidRequest_ReturnsSuccess() {
        // Given
        when(terraformParser.parse(any(List.class))).thenReturn(mockParsedTerraform);
        when(resourceExtractor.extractComponents(mockParsedTerraform)).thenReturn(mockComponents);
        doNothing().when(neo4jMapper).mapToGraph(mockComponents);

        // When
        ResponseEntity<?> response = terraformController.parseTerraformContent(validParseRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ParseResponse);
        
        ParseResponse parseResponse = (ParseResponse) response.getBody();
        assertTrue(parseResponse.isSuccess());
        assertEquals(2, parseResponse.getResourceCount());
        assertEquals(1, parseResponse.getIdentityResourceCount());
        assertEquals(1, parseResponse.getRegularResourceCount());
        assertTrue(parseResponse.getProvidersDetected().contains("GCP"));
        assertEquals(2, parseResponse.getResourceTypeSummary().size());

        // Verify service calls
        verify(terraformParser).parse(any(List.class));
        verify(resourceExtractor).extractComponents(mockParsedTerraform);
        verify(neo4jMapper).mapToGraph(mockComponents);
    }

    @Test
    void parseTerraformContent_ParsingErrors_ReturnsUnprocessableEntity() {
        // Given
        ParsedTerraform parsedWithErrors = ParsedTerraform.builder()
                .resources(Arrays.asList())
                .parseErrors(Arrays.asList("Invalid HCL syntax at line 5", "Unknown resource type"))
                .build();

        when(terraformParser.parse(any(List.class))).thenReturn(parsedWithErrors);

        // When
        ResponseEntity<?> response = terraformController.parseTerraformContent(validParseRequest);

        // Then
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());

        // Verify parsing was attempted but extraction and mapping were not
        verify(terraformParser).parse(any(List.class));
        verifyNoInteractions(resourceExtractor, neo4jMapper);
    }

    @Test
    void parseTerraformContent_ServiceException_ReturnsInternalServerError() {
        // Given
        when(terraformParser.parse(any(List.class))).thenThrow(new RuntimeException("Service error"));

        // When
        ResponseEntity<?> response = terraformController.parseTerraformContent(validParseRequest);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        // Verify parsing was attempted
        verify(terraformParser).parse(any(List.class));
        verifyNoInteractions(resourceExtractor, neo4jMapper);
    }

    @Test
    void parseTerraformZip_ValidZipFile_ReturnsSuccess() throws Exception {
        // Given
        MockMultipartFile zipFile = new MockMultipartFile(
                "file",
                "terraform.zip",
                "application/zip",
                "mock zip content".getBytes()
        );

        when(fileInputHandler.readTerraformFiles(anyString())).thenReturn(Arrays.asList());
        when(terraformParser.parse(any(List.class))).thenReturn(mockParsedTerraform);
        when(resourceExtractor.extractComponents(mockParsedTerraform)).thenReturn(mockComponents);
        doNothing().when(neo4jMapper).mapToGraph(mockComponents);

        // When
        ResponseEntity<?> response = terraformController.parseTerraformZip(zipFile);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ParseResponse);
        
        ParseResponse parseResponse = (ParseResponse) response.getBody();
        assertTrue(parseResponse.isSuccess());

        // Verify service calls
        verify(fileInputHandler).readTerraformFiles(anyString());
        verify(terraformParser).parse(any(List.class));
        verify(resourceExtractor).extractComponents(mockParsedTerraform);
        verify(neo4jMapper).mapToGraph(mockComponents);
    }

    @Test
    void parseTerraformZip_EmptyFile_ReturnsBadRequest() {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.zip",
                "application/zip",
                new byte[0]
        );

        // When
        ResponseEntity<?> response = terraformController.parseTerraformZip(emptyFile);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        // Verify no service calls were made
        verifyNoInteractions(fileInputHandler, terraformParser, resourceExtractor, neo4jMapper);
    }

    @Test
    void parseTerraformZip_NonZipFile_ReturnsBadRequest() {
        // Given
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "terraform.txt",
                "text/plain",
                "not a zip file".getBytes()
        );

        // When
        ResponseEntity<?> response = terraformController.parseTerraformZip(textFile);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        // Verify no service calls were made
        verifyNoInteractions(fileInputHandler, terraformParser, resourceExtractor, neo4jMapper);
    }

    @Test
    void parseTerraformZip_FileReadingException_ReturnsInternalServerError() throws Exception {
        // Given
        MockMultipartFile zipFile = new MockMultipartFile(
                "file",
                "corrupt.zip",
                "application/zip",
                "mock zip content".getBytes()
        );

        when(fileInputHandler.readTerraformFiles(anyString()))
                .thenThrow(new RuntimeException("Failed to read zip file"));

        // When
        ResponseEntity<?> response = terraformController.parseTerraformZip(zipFile);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        // Verify file reading was attempted
        verify(fileInputHandler).readTerraformFiles(anyString());
        verifyNoInteractions(terraformParser, resourceExtractor, neo4jMapper);
    }
}