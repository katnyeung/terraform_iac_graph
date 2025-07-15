package com.terraform.neo4j.service;

import com.terraform.neo4j.model.InfrastructureComponent;
import com.terraform.neo4j.model.IdentityType;
import com.terraform.neo4j.model.ParsedTerraform;
import com.terraform.neo4j.model.TerraformResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Component responsible for extracting infrastructure components from parsed Terraform resources.
 * Handles provider detection, identity resource classification, and component ID generation.
 */
@Component
public class BasicResourceExtractor {

    /**
     * Extracts infrastructure components from parsed Terraform configuration.
     *
     * @param parsed The parsed Terraform configuration
     * @return List of infrastructure components
     */
    public List<InfrastructureComponent> extractComponents(ParsedTerraform parsed) {
        if (parsed == null || parsed.getResources() == null) {
            return List.of();
        }

        return parsed.getResources().stream()
                .map(this::createComponent)
                .collect(Collectors.toList());
    }

    /**
     * Creates an infrastructure component from a Terraform resource.
     *
     * @param resource The Terraform resource
     * @return The infrastructure component
     */
    private InfrastructureComponent createComponent(TerraformResource resource) {
        return InfrastructureComponent.builder()
                .id(generateId(resource))
                .type(resource.getType())
                .name(resource.getName())
                .provider(detectProvider(resource.getType()))
                .properties(resource.getArguments())
                .identityType(detectIdentityType(resource))
                .build();
    }

    /**
     * Generates a unique ID for a component based on the resource.
     *
     * @param resource The Terraform resource
     * @return A unique identifier
     */
    private String generateId(TerraformResource resource) {
        // Create a deterministic ID based on resource type and name
        String baseId = resource.getType() + "." + resource.getName();
        // Add a UUID suffix to ensure uniqueness
        return baseId + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Detects the cloud provider based on resource type prefix.
     *
     * @param resourceType The Terraform resource type
     * @return The detected provider (GCP, AWS, Azure, or UNKNOWN)
     */
    public String detectProvider(String resourceType) {
        if (resourceType == null) {
            return "UNKNOWN";
        }

        String lowerType = resourceType.toLowerCase();

        // Google Cloud Platform resources
        if (lowerType.startsWith("google_") || lowerType.startsWith("gcp_")) {
            return "GCP";
        }

        // Amazon Web Services resources
        if (lowerType.startsWith("aws_")) {
            return "AWS";
        }

        // Microsoft Azure resources
        if (lowerType.startsWith("azurerm_") || lowerType.startsWith("azure_")) {
            return "AZURE";
        }

        // Other providers
        if (lowerType.startsWith("kubernetes_") || lowerType.startsWith("k8s_")) {
            return "KUBERNETES";
        }

        if (lowerType.startsWith("helm_")) {
            return "HELM";
        }

        if (lowerType.startsWith("docker_")) {
            return "DOCKER";
        }

        return "UNKNOWN";
    }

    /**
     * Detects if a resource is an identity resource based on its type.
     *
     * @param resource The Terraform resource
     * @return The identity type classification
     */
    public IdentityType detectIdentityType(TerraformResource resource) {
        if (resource == null || resource.getType() == null) {
            return IdentityType.REGULAR_RESOURCE;
        }

        String resourceType = resource.getType().toLowerCase();

        // Google Cloud Platform identity resources
        if (resourceType.equals("google_service_account") ||
            resourceType.equals("google_service_account_key") ||
            resourceType.equals("google_service_account_iam_binding") ||
            resourceType.equals("google_service_account_iam_member") ||
            resourceType.equals("google_service_account_iam_policy")) {
            return IdentityType.IDENTITY_RESOURCE;
        }

        // AWS identity resources
        if (resourceType.equals("aws_iam_role") ||
            resourceType.equals("aws_iam_user") ||
            resourceType.equals("aws_iam_group") ||
            resourceType.equals("aws_iam_policy") ||
            resourceType.equals("aws_iam_role_policy") ||
            resourceType.equals("aws_iam_user_policy") ||
            resourceType.equals("aws_iam_group_policy") ||
            resourceType.equals("aws_iam_role_policy_attachment") ||
            resourceType.equals("aws_iam_user_policy_attachment") ||
            resourceType.equals("aws_iam_group_policy_attachment") ||
            resourceType.equals("aws_iam_access_key") ||
            resourceType.equals("aws_iam_instance_profile")) {
            return IdentityType.IDENTITY_RESOURCE;
        }

        // Azure identity resources
        if (resourceType.equals("azurerm_user_assigned_identity") ||
            resourceType.equals("azurerm_service_principal") ||
            resourceType.equals("azurerm_service_principal_password") ||
            resourceType.equals("azurerm_service_principal_certificate") ||
            resourceType.equals("azurerm_role_assignment") ||
            resourceType.equals("azurerm_role_definition") ||
            resourceType.equals("azurerm_application") ||
            resourceType.equals("azurerm_application_password") ||
            resourceType.equals("azurerm_application_certificate")) {
            return IdentityType.IDENTITY_RESOURCE;
        }

        // Generic identity patterns (for other providers or custom resources)
        if (resourceType.contains("service_account") ||
            resourceType.contains("iam_role") ||
            resourceType.contains("iam_user") ||
            resourceType.contains("iam_group") ||
            resourceType.contains("iam_policy") ||
            resourceType.contains("user_assigned_identity") ||
            resourceType.contains("service_principal") ||
            resourceType.contains("role_assignment") ||
            resourceType.contains("role_definition")) {
            return IdentityType.IDENTITY_RESOURCE;
        }

        return IdentityType.REGULAR_RESOURCE;
    }
}