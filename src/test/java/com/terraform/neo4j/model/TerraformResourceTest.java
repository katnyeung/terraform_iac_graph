package com.terraform.neo4j.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TerraformResource model.
 */
class TerraformResourceTest {

    @Test
    @DisplayName("Should create TerraformResource with builder pattern")
    void shouldCreateWithBuilder() {
        // Given
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-vm");
        arguments.put("machine_type", "e2-medium");

        // When
        TerraformResource resource = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm")
                .arguments(arguments)
                .build();

        // Then
        assertNotNull(resource);
        assertEquals("google_compute_instance", resource.getType());
        assertEquals("vm", resource.getName());
        assertEquals("test-vm", resource.getArgument("name"));
        assertEquals("e2-medium", resource.getArgument("machine_type"));
        assertEquals("google_compute_instance.vm", resource.getResourceId());
    }

    @Test
    @DisplayName("Should create TerraformResource with individual argument additions")
    void shouldCreateWithIndividualArguments() {
        // When
        TerraformResource resource = TerraformResource.builder()
                .type("aws_instance")
                .name("web")
                .addArgument("ami", "ami-12345")
                .addArgument("instance_type", "t2.micro")
                .build();

        // Then
        assertNotNull(resource);
        assertEquals("aws_instance", resource.getType());
        assertEquals("web", resource.getName());
        assertEquals("ami-12345", resource.getArgument("ami"));
        assertEquals("t2.micro", resource.getArgument("instance_type"));
        assertEquals(2, resource.getArguments().size());
    }

    @Test
    @DisplayName("Should handle null arguments gracefully")
    void shouldHandleNullArguments() {
        // When
        TerraformResource resource = TerraformResource.builder()
                .type("google_storage_bucket")
                .name("bucket")
                .arguments(null)
                .build();

        // Then
        assertNotNull(resource);
        assertNotNull(resource.getArguments());
        assertTrue(resource.getArguments().isEmpty());
    }

    @Test
    @DisplayName("Should create with default constructor")
    void shouldCreateWithDefaultConstructor() {
        // When
        TerraformResource resource = new TerraformResource();

        // Then
        assertNotNull(resource);
        assertNull(resource.getType());
        assertNull(resource.getName());
        assertNotNull(resource.getArguments());
        assertTrue(resource.getArguments().isEmpty());
    }

    @Test
    @DisplayName("Should create with full constructor")
    void shouldCreateWithFullConstructor() {
        // Given
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("location", "us-central1");

        // When
        TerraformResource resource = new TerraformResource("google_storage_bucket", "bucket", arguments);

        // Then
        assertNotNull(resource);
        assertEquals("google_storage_bucket", resource.getType());
        assertEquals("bucket", resource.getName());
        assertEquals("us-central1", resource.getArgument("location"));
    }

    @Test
    @DisplayName("Should add and retrieve arguments correctly")
    void shouldAddAndRetrieveArguments() {
        // Given
        TerraformResource resource = new TerraformResource();

        // When
        resource.addArgument("key1", "value1");
        resource.addArgument("key2", 42);
        resource.addArgument("key3", true);

        // Then
        assertEquals("value1", resource.getArgument("key1"));
        assertEquals(42, resource.getArgument("key2"));
        assertEquals(true, resource.getArgument("key3"));
        assertNull(resource.getArgument("nonexistent"));
    }

    @Test
    @DisplayName("Should generate correct resource ID")
    void shouldGenerateCorrectResourceId() {
        // Given
        TerraformResource resource = TerraformResource.builder()
                .type("azurerm_virtual_machine")
                .name("example")
                .build();

        // When
        String resourceId = resource.getResourceId();

        // Then
        assertEquals("azurerm_virtual_machine.example", resourceId);
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        // Given
        Map<String, Object> arguments1 = new HashMap<>();
        arguments1.put("name", "test");

        Map<String, Object> arguments2 = new HashMap<>();
        arguments2.put("name", "test");

        TerraformResource resource1 = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm")
                .arguments(arguments1)
                .build();

        TerraformResource resource2 = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm")
                .arguments(arguments2)
                .build();

        TerraformResource resource3 = TerraformResource.builder()
                .type("google_compute_instance")
                .name("different")
                .arguments(arguments1)
                .build();

        // Then
        assertEquals(resource1, resource2);
        assertEquals(resource1.hashCode(), resource2.hashCode());
        assertNotEquals(resource1, resource3);
        assertNotEquals(resource1.hashCode(), resource3.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToString() {
        // Given
        TerraformResource resource = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm")
                .addArgument("name", "test-vm")
                .addArgument("zone", "us-central1-a")
                .build();

        // When
        String toString = resource.toString();

        // Then
        assertTrue(toString.contains("google_compute_instance"));
        assertTrue(toString.contains("vm"));
        assertTrue(toString.contains("argumentCount=2"));
    }

    @Test
    @DisplayName("Should handle setters correctly")
    void shouldHandleSetters() {
        // Given
        TerraformResource resource = new TerraformResource();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("test", "value");

        // When
        resource.setType("test_type");
        resource.setName("test_name");
        resource.setArguments(arguments);

        // Then
        assertEquals("test_type", resource.getType());
        assertEquals("test_name", resource.getName());
        assertEquals("value", resource.getArgument("test"));
    }
}