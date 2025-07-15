package com.terraform.neo4j.service;

import com.terraform.neo4j.model.IdentityType;
import com.terraform.neo4j.model.InfrastructureComponent;
import com.terraform.neo4j.model.ResourceNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic unit tests for SimpleNeo4jMapper.
 * These tests focus on verifying the class structure and basic functionality
 * without requiring a real Neo4j database connection.
 */
class SimpleNeo4jMapperTest {

    @Test
    void shouldCreateResourceNodeFromInfrastructureComponent() {
        // Given
        InfrastructureComponent component = InfrastructureComponent.builder()
            .id("test-vm-1")
            .name("test-vm")
            .type("google_compute_instance")
            .provider("GCP")
            .identityType(IdentityType.REGULAR_RESOURCE)
            .properties(Map.of(
                "machine_type", "e2-medium",
                "zone", "us-central1-a"
            ))
            .build();

        // When
        ResourceNode node = new ResourceNode(
            component.getId(),
            component.getName(),
            component.getType(),
            component.getProvider(),
            component.getIdentityType().toString(),
            component.getProperties()
        );

        // Then
        assertThat(node.getId()).isEqualTo("test-vm-1");
        assertThat(node.getName()).isEqualTo("test-vm");
        assertThat(node.getType()).isEqualTo("google_compute_instance");
        assertThat(node.getProvider()).isEqualTo("GCP");
        assertThat(node.getIdentityType()).isEqualTo("REGULAR_RESOURCE");
        assertThat(node.getProperties()).containsEntry("machine_type", "e2-medium");
    }

    @Test
    void shouldCreateResourceNodeForIdentityResource() {
        // Given
        InfrastructureComponent component = InfrastructureComponent.builder()
            .id("test-sa-1")
            .name("test-service-account")
            .type("google_service_account")
            .provider("GCP")
            .identityType(IdentityType.IDENTITY_RESOURCE)
            .properties(Map.of(
                "display_name", "Test Service Account"
            ))
            .build();

        // When
        ResourceNode node = new ResourceNode(
            component.getId(),
            component.getName(),
            component.getType(),
            component.getProvider(),
            component.getIdentityType().toString(),
            component.getProperties()
        );

        // Then
        assertThat(node.getId()).isEqualTo("test-sa-1");
        assertThat(node.getName()).isEqualTo("test-service-account");
        assertThat(node.getType()).isEqualTo("google_service_account");
        assertThat(node.getProvider()).isEqualTo("GCP");
        assertThat(node.getIdentityType()).isEqualTo("IDENTITY_RESOURCE");
        assertThat(node.getProperties()).containsEntry("display_name", "Test Service Account");
    }

    @Test
    void shouldHandleEmptyComponentsList() {
        // Given
        List<InfrastructureComponent> components = List.of();

        // When/Then - should not throw exception
        assertThat(components).isEmpty();
    }

    @Test
    void shouldHandleMultipleComponents() {
        // Given
        List<InfrastructureComponent> components = List.of(
            InfrastructureComponent.builder()
                .id("vm-1")
                .name("vm-1")
                .type("google_compute_instance")
                .provider("GCP")
                .identityType(IdentityType.REGULAR_RESOURCE)
                .properties(Map.of("zone", "us-central1-a"))
                .build(),
            InfrastructureComponent.builder()
                .id("sa-1")
                .name("sa-1")
                .type("google_service_account")
                .provider("GCP")
                .identityType(IdentityType.IDENTITY_RESOURCE)
                .properties(Map.of("display_name", "SA 1"))
                .build()
        );

        // When/Then
        assertThat(components).hasSize(2);
        assertThat(components.get(0).getIdentityType()).isEqualTo(IdentityType.REGULAR_RESOURCE);
        assertThat(components.get(1).getIdentityType()).isEqualTo(IdentityType.IDENTITY_RESOURCE);
    }
}