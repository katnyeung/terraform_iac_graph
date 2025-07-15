package com.terraform.neo4j.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ParsedTerraform model.
 */
class ParsedTerraformTest {

    @Test
    @DisplayName("Should create ParsedTerraform with builder pattern")
    void shouldCreateWithBuilder() {
        // Given
        TerraformResource resource1 = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm1")
                .build();

        TerraformResource resource2 = TerraformResource.builder()
                .type("google_storage_bucket")
                .name("bucket1")
                .build();

        List<TerraformResource> resources = Arrays.asList(resource1, resource2);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("project_id", "test-project");
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("instance_ip", "output config");

        // When
        ParsedTerraform parsed = ParsedTerraform.builder()
                .resources(resources)
                .variables(variables)
                .outputs(outputs)
                .addParseError("test error")
                .build();

        // Then
        assertNotNull(parsed);
        assertEquals(2, parsed.getResourceCount());
        assertEquals(1, parsed.getVariables().size());
        assertEquals(1, parsed.getOutputs().size());
        assertTrue(parsed.hasParseErrors());
        assertEquals(1, parsed.getParseErrors().size());
        assertEquals("test error", parsed.getParseErrors().get(0));
    }

    @Test
    @DisplayName("Should create with default constructor")
    void shouldCreateWithDefaultConstructor() {
        // When
        ParsedTerraform parsed = new ParsedTerraform();

        // Then
        assertNotNull(parsed);
        assertEquals(0, parsed.getResourceCount());
        assertNotNull(parsed.getResources());
        assertNotNull(parsed.getVariables());
        assertNotNull(parsed.getOutputs());
        assertNotNull(parsed.getProviders());
        assertNotNull(parsed.getParseErrors());
        assertFalse(parsed.hasParseErrors());
        assertFalse(parsed.isSuccessful()); // No resources
    }

    @Test
    @DisplayName("Should create with resources constructor")
    void shouldCreateWithResourcesConstructor() {
        // Given
        TerraformResource resource = TerraformResource.builder()
                .type("aws_instance")
                .name("web")
                .build();

        List<TerraformResource> resources = Arrays.asList(resource);

        // When
        ParsedTerraform parsed = new ParsedTerraform(resources);

        // Then
        assertNotNull(parsed);
        assertEquals(1, parsed.getResourceCount());
        assertEquals("aws_instance", parsed.getResources().get(0).getType());
        assertFalse(parsed.hasParseErrors());
        assertTrue(parsed.isSuccessful());
    }

    @Test
    @DisplayName("Should add resources individually")
    void shouldAddResourcesIndividually() {
        // Given
        ParsedTerraform parsed = new ParsedTerraform();
        TerraformResource resource1 = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm1")
                .build();
        TerraformResource resource2 = TerraformResource.builder()
                .type("google_storage_bucket")
                .name("bucket1")
                .build();

        // When
        parsed.addResource(resource1);
        parsed.addResource(resource2);

        // Then
        assertEquals(2, parsed.getResourceCount());
        assertEquals("google_compute_instance", parsed.getResources().get(0).getType());
        assertEquals("google_storage_bucket", parsed.getResources().get(1).getType());
    }

    @Test
    @DisplayName("Should filter resources by type")
    void shouldFilterResourcesByType() {
        // Given
        TerraformResource vm1 = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm1")
                .build();
        TerraformResource vm2 = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm2")
                .build();
        TerraformResource bucket = TerraformResource.builder()
                .type("google_storage_bucket")
                .name("bucket1")
                .build();

        ParsedTerraform parsed = ParsedTerraform.builder()
                .addResource(vm1)
                .addResource(vm2)
                .addResource(bucket)
                .build();

        // When
        List<TerraformResource> computeInstances = parsed.getResourcesByType("google_compute_instance");
        List<TerraformResource> storageBuckets = parsed.getResourcesByType("google_storage_bucket");
        List<TerraformResource> nonExistent = parsed.getResourcesByType("non_existent_type");

        // Then
        assertEquals(2, computeInstances.size());
        assertEquals(1, storageBuckets.size());
        assertEquals(0, nonExistent.size());
    }

    @Test
    @DisplayName("Should handle parse errors correctly")
    void shouldHandleParseErrors() {
        // Given
        ParsedTerraform parsed = new ParsedTerraform();

        // When
        parsed.addParseError("Error 1");
        parsed.addParseError("Error 2");
        parsed.addParseError(null); // Should be ignored
        parsed.addParseError(""); // Should be ignored
        parsed.addParseError("   "); // Should be ignored

        // Then
        assertTrue(parsed.hasParseErrors());
        assertEquals(2, parsed.getParseErrors().size());
        assertTrue(parsed.getParseErrors().contains("Error 1"));
        assertTrue(parsed.getParseErrors().contains("Error 2"));
    }

    @Test
    @DisplayName("Should determine success correctly")
    void shouldDetermineSuccess() {
        // Given
        TerraformResource resource = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm")
                .build();

        // Successful case: has resources, no errors
        ParsedTerraform successful = ParsedTerraform.builder()
                .addResource(resource)
                .build();

        // Failed case: has errors
        ParsedTerraform withErrors = ParsedTerraform.builder()
                .addResource(resource)
                .addParseError("Parse error")
                .build();

        // Failed case: no resources
        ParsedTerraform noResources = new ParsedTerraform();

        // Then
        assertTrue(successful.isSuccessful());
        assertFalse(withErrors.isSuccessful());
        assertFalse(noResources.isSuccessful());
    }

    @Test
    @DisplayName("Should handle null collections gracefully")
    void shouldHandleNullCollections() {
        // When
        ParsedTerraform parsed = ParsedTerraform.builder()
                .resources(null)
                .variables(null)
                .outputs(null)
                .providers(null)
                .parseErrors(null)
                .build();

        // Then
        assertNotNull(parsed.getResources());
        assertNotNull(parsed.getVariables());
        assertNotNull(parsed.getOutputs());
        assertNotNull(parsed.getProviders());
        assertNotNull(parsed.getParseErrors());
        assertTrue(parsed.getResources().isEmpty());
        assertTrue(parsed.getVariables().isEmpty());
        assertTrue(parsed.getOutputs().isEmpty());
        assertTrue(parsed.getProviders().isEmpty());
        assertTrue(parsed.getParseErrors().isEmpty());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        // Given
        TerraformResource resource = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm")
                .build();

        ParsedTerraform parsed1 = ParsedTerraform.builder()
                .addResource(resource)
                .build();

        ParsedTerraform parsed2 = ParsedTerraform.builder()
                .addResource(resource)
                .build();

        ParsedTerraform parsed3 = ParsedTerraform.builder()
                .addParseError("error")
                .build();

        // Then
        assertEquals(parsed1, parsed2);
        assertEquals(parsed1.hashCode(), parsed2.hashCode());
        assertNotEquals(parsed1, parsed3);
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToString() {
        // Given
        TerraformResource resource = TerraformResource.builder()
                .type("google_compute_instance")
                .name("vm")
                .build();

        Map<String, Object> variables = new HashMap<>();
        variables.put("project_id", "test");

        ParsedTerraform parsed = ParsedTerraform.builder()
                .addResource(resource)
                .variables(variables)
                .addParseError("test error")
                .build();

        // When
        String toString = parsed.toString();

        // Then
        assertTrue(toString.contains("resourceCount=1"));
        assertTrue(toString.contains("variableCount=1"));
        assertTrue(toString.contains("errorCount=1"));
    }

    @Test
    @DisplayName("Should handle setters correctly")
    void shouldHandleSetters() {
        // Given
        ParsedTerraform parsed = new ParsedTerraform();
        TerraformResource resource = TerraformResource.builder()
                .type("test_type")
                .name("test_name")
                .build();
        List<TerraformResource> resources = Arrays.asList(resource);
        Map<String, Object> variables = new HashMap<>();
        variables.put("test", "value");

        // When
        parsed.setResources(resources);
        parsed.setVariables(variables);

        // Then
        assertEquals(1, parsed.getResourceCount());
        assertEquals("test_type", parsed.getResources().get(0).getType());
        assertEquals("value", parsed.getVariables().get("test"));
    }
}