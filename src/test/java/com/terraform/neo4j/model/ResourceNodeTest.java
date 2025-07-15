package com.terraform.neo4j.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ResourceNode entity.
 */
class ResourceNodeTest {

    @Test
    void shouldCreateResourceNodeWithAllProperties() {
        // Given
        Map<String, Object> properties = new HashMap<>();
        properties.put("machine_type", "e2-medium");
        properties.put("zone", "us-central1-a");

        // When
        ResourceNode node = new ResourceNode(
            "vm-1",
            "web-server",
            "google_compute_instance",
            "GCP",
            "REGULAR_RESOURCE",
            properties
        );

        // Then
        assertThat(node.getId()).isEqualTo("vm-1");
        assertThat(node.getName()).isEqualTo("web-server");
        assertThat(node.getType()).isEqualTo("google_compute_instance");
        assertThat(node.getProvider()).isEqualTo("GCP");
        assertThat(node.getIdentityType()).isEqualTo("REGULAR_RESOURCE");
        assertThat(node.getProperties()).containsEntry("machine_type", "e2-medium");
        assertThat(node.getProperties()).containsEntry("zone", "us-central1-a");
    }

    @Test
    void shouldCreateEmptyResourceNode() {
        // When
        ResourceNode node = new ResourceNode();

        // Then
        assertThat(node.getId()).isNull();
        assertThat(node.getName()).isNull();
        assertThat(node.getType()).isNull();
        assertThat(node.getProvider()).isNull();
        assertThat(node.getIdentityType()).isNull();
        assertThat(node.getProperties()).isNull();
        assertThat(node.getNodeId()).isNull();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        // Given
        ResourceNode node = new ResourceNode();
        Map<String, Object> properties = Map.of("key", "value");

        // When
        node.setId("test-id");
        node.setName("test-name");
        node.setType("test-type");
        node.setProvider("TEST");
        node.setIdentityType("IDENTITY_RESOURCE");
        node.setProperties(properties);
        node.setNodeId(123L);

        // Then
        assertThat(node.getId()).isEqualTo("test-id");
        assertThat(node.getName()).isEqualTo("test-name");
        assertThat(node.getType()).isEqualTo("test-type");
        assertThat(node.getProvider()).isEqualTo("TEST");
        assertThat(node.getIdentityType()).isEqualTo("IDENTITY_RESOURCE");
        assertThat(node.getProperties()).isEqualTo(properties);
        assertThat(node.getNodeId()).isEqualTo(123L);
    }

    @Test
    void shouldImplementEqualsAndHashCodeBasedOnId() {
        // Given
        ResourceNode node1 = new ResourceNode();
        node1.setId("same-id");
        node1.setName("name1");

        ResourceNode node2 = new ResourceNode();
        node2.setId("same-id");
        node2.setName("name2");

        ResourceNode node3 = new ResourceNode();
        node3.setId("different-id");
        node3.setName("name1");

        // Then
        assertThat(node1).isEqualTo(node2);
        assertThat(node1).isNotEqualTo(node3);
        assertThat(node1.hashCode()).isEqualTo(node2.hashCode());
        assertThat(node1.hashCode()).isNotEqualTo(node3.hashCode());
    }

    @Test
    void shouldHandleNullInEqualsAndHashCode() {
        // Given
        ResourceNode node1 = new ResourceNode();
        ResourceNode node2 = new ResourceNode();
        ResourceNode node3 = new ResourceNode();
        node3.setId("test-id");

        // Then
        assertThat(node1).isEqualTo(node2);
        assertThat(node1).isNotEqualTo(node3);
        assertThat(node1).isNotEqualTo(null);
        assertThat(node1).isNotEqualTo("string");
    }

    @Test
    void shouldProvideStringRepresentation() {
        // Given
        Map<String, Object> properties = Map.of("key", "value");
        ResourceNode node = new ResourceNode(
            "vm-1",
            "web-server",
            "google_compute_instance",
            "GCP",
            "REGULAR_RESOURCE",
            properties
        );
        node.setNodeId(123L);

        // When
        String toString = node.toString();

        // Then
        assertThat(toString).contains("ResourceNode{");
        assertThat(toString).contains("nodeId=123");
        assertThat(toString).contains("id='vm-1'");
        assertThat(toString).contains("name='web-server'");
        assertThat(toString).contains("type='google_compute_instance'");
        assertThat(toString).contains("provider='GCP'");
        assertThat(toString).contains("identityType='REGULAR_RESOURCE'");
        assertThat(toString).contains("properties={key=value}");
    }
}