package com.terraform.neo4j.service;

import com.terraform.neo4j.model.InfrastructureComponent;
import com.terraform.neo4j.model.IdentityType;
import com.terraform.neo4j.model.ParsedTerraform;
import com.terraform.neo4j.model.TerraformResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BasicResourceExtractor component.
 * Tests provider detection, identity resource classification, and component extraction.
 */
class BasicResourceExtractorTest {

    private BasicResourceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new BasicResourceExtractor();
    }

    @Nested
    @DisplayName("Provider Detection Tests")
    class ProviderDetectionTests {

        @Test
        @DisplayName("Should detect GCP provider from google_ prefix")
        void shouldDetectGcpProvider() {
            assertThat(extractor.detectProvider("google_compute_instance")).isEqualTo("GCP");
            assertThat(extractor.detectProvider("google_service_account")).isEqualTo("GCP");
            assertThat(extractor.detectProvider("google_storage_bucket")).isEqualTo("GCP");
            assertThat(extractor.detectProvider("gcp_custom_resource")).isEqualTo("GCP");
        }

        @Test
        @DisplayName("Should detect AWS provider from aws_ prefix")
        void shouldDetectAwsProvider() {
            assertThat(extractor.detectProvider("aws_instance")).isEqualTo("AWS");
            assertThat(extractor.detectProvider("aws_iam_role")).isEqualTo("AWS");
            assertThat(extractor.detectProvider("aws_s3_bucket")).isEqualTo("AWS");
        }

        @Test
        @DisplayName("Should detect Azure provider from azurerm_ prefix")
        void shouldDetectAzureProvider() {
            assertThat(extractor.detectProvider("azurerm_virtual_machine")).isEqualTo("AZURE");
            assertThat(extractor.detectProvider("azurerm_service_principal")).isEqualTo("AZURE");
            assertThat(extractor.detectProvider("azure_custom_resource")).isEqualTo("AZURE");
        }

        @Test
        @DisplayName("Should detect other known providers")
        void shouldDetectOtherProviders() {
            assertThat(extractor.detectProvider("kubernetes_deployment")).isEqualTo("KUBERNETES");
            assertThat(extractor.detectProvider("k8s_service")).isEqualTo("KUBERNETES");
            assertThat(extractor.detectProvider("helm_release")).isEqualTo("HELM");
            assertThat(extractor.detectProvider("docker_image")).isEqualTo("DOCKER");
        }

        @Test
        @DisplayName("Should return UNKNOWN for unrecognized providers")
        void shouldReturnUnknownForUnrecognizedProviders() {
            assertThat(extractor.detectProvider("custom_resource")).isEqualTo("UNKNOWN");
            assertThat(extractor.detectProvider("random_string")).isEqualTo("UNKNOWN");
            assertThat(extractor.detectProvider("local_file")).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("Should handle null and empty resource types")
        void shouldHandleNullAndEmptyResourceTypes() {
            assertThat(extractor.detectProvider(null)).isEqualTo("UNKNOWN");
            assertThat(extractor.detectProvider("")).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("Should be case insensitive")
        void shouldBeCaseInsensitive() {
            assertThat(extractor.detectProvider("GOOGLE_COMPUTE_INSTANCE")).isEqualTo("GCP");
            assertThat(extractor.detectProvider("AWS_INSTANCE")).isEqualTo("AWS");
            assertThat(extractor.detectProvider("AZURERM_VIRTUAL_MACHINE")).isEqualTo("AZURE");
        }
    }

    @Nested
    @DisplayName("Identity Type Detection Tests")
    class IdentityTypeDetectionTests {

        @Test
        @DisplayName("Should detect GCP identity resources")
        void shouldDetectGcpIdentityResources() {
            TerraformResource serviceAccount = createResource("google_service_account", "test_sa");
            TerraformResource serviceAccountKey = createResource("google_service_account_key", "test_key");
            TerraformResource iamBinding = createResource("google_service_account_iam_binding", "test_binding");

            assertThat(extractor.detectIdentityType(serviceAccount)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(serviceAccountKey)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(iamBinding)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
        }

        @Test
        @DisplayName("Should detect AWS identity resources")
        void shouldDetectAwsIdentityResources() {
            TerraformResource iamRole = createResource("aws_iam_role", "test_role");
            TerraformResource iamUser = createResource("aws_iam_user", "test_user");
            TerraformResource iamGroup = createResource("aws_iam_group", "test_group");
            TerraformResource iamPolicy = createResource("aws_iam_policy", "test_policy");
            TerraformResource instanceProfile = createResource("aws_iam_instance_profile", "test_profile");

            assertThat(extractor.detectIdentityType(iamRole)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(iamUser)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(iamGroup)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(iamPolicy)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(instanceProfile)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
        }

        @Test
        @DisplayName("Should detect Azure identity resources")
        void shouldDetectAzureIdentityResources() {
            TerraformResource userIdentity = createResource("azurerm_user_assigned_identity", "test_identity");
            TerraformResource servicePrincipal = createResource("azurerm_service_principal", "test_sp");
            TerraformResource roleAssignment = createResource("azurerm_role_assignment", "test_assignment");
            TerraformResource application = createResource("azurerm_application", "test_app");

            assertThat(extractor.detectIdentityType(userIdentity)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(servicePrincipal)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(roleAssignment)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(application)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
        }

        @Test
        @DisplayName("Should detect generic identity patterns")
        void shouldDetectGenericIdentityPatterns() {
            TerraformResource customServiceAccount = createResource("custom_service_account", "test");
            TerraformResource customIamRole = createResource("provider_iam_role", "test");
            TerraformResource customUserIdentity = createResource("some_user_assigned_identity", "test");

            assertThat(extractor.detectIdentityType(customServiceAccount)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(customIamRole)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(extractor.detectIdentityType(customUserIdentity)).isEqualTo(IdentityType.IDENTITY_RESOURCE);
        }

        @Test
        @DisplayName("Should classify regular resources correctly")
        void shouldClassifyRegularResourcesCorrectly() {
            TerraformResource computeInstance = createResource("google_compute_instance", "test_vm");
            TerraformResource ec2Instance = createResource("aws_instance", "test_instance");
            TerraformResource azureVm = createResource("azurerm_virtual_machine", "test_vm");
            TerraformResource storageBucket = createResource("google_storage_bucket", "test_bucket");

            assertThat(extractor.detectIdentityType(computeInstance)).isEqualTo(IdentityType.REGULAR_RESOURCE);
            assertThat(extractor.detectIdentityType(ec2Instance)).isEqualTo(IdentityType.REGULAR_RESOURCE);
            assertThat(extractor.detectIdentityType(azureVm)).isEqualTo(IdentityType.REGULAR_RESOURCE);
            assertThat(extractor.detectIdentityType(storageBucket)).isEqualTo(IdentityType.REGULAR_RESOURCE);
        }

        @Test
        @DisplayName("Should handle null and invalid resources")
        void shouldHandleNullAndInvalidResources() {
            assertThat(extractor.detectIdentityType(null)).isEqualTo(IdentityType.REGULAR_RESOURCE);
            
            TerraformResource resourceWithNullType = new TerraformResource();
            resourceWithNullType.setName("test");
            assertThat(extractor.detectIdentityType(resourceWithNullType)).isEqualTo(IdentityType.REGULAR_RESOURCE);
        }
    }

    @Nested
    @DisplayName("Component Extraction Tests")
    class ComponentExtractionTests {

        @Test
        @DisplayName("Should extract components from parsed Terraform")
        void shouldExtractComponentsFromParsedTerraform() {
            // Given
            Map<String, Object> properties = Map.of(
                "machine_type", "e2-medium",
                "zone", "us-central1-a"
            );
            
            TerraformResource resource1 = createResource("google_compute_instance", "web_server", properties);
            TerraformResource resource2 = createResource("google_service_account", "app_sa", Map.of());
            
            ParsedTerraform parsed = ParsedTerraform.builder()
                .resources(List.of(resource1, resource2))
                .build();

            // When
            List<InfrastructureComponent> components = extractor.extractComponents(parsed);

            // Then
            assertThat(components).hasSize(2);
            
            InfrastructureComponent component1 = components.get(0);
            assertThat(component1.getName()).isEqualTo("web_server");
            assertThat(component1.getType()).isEqualTo("google_compute_instance");
            assertThat(component1.getProvider()).isEqualTo("GCP");
            assertThat(component1.getIdentityType()).isEqualTo(IdentityType.REGULAR_RESOURCE);
            assertThat(component1.getProperties()).containsEntry("machine_type", "e2-medium");
            assertThat(component1.getId()).isNotNull();
            assertThat(component1.getId()).startsWith("google_compute_instance.web_server.");

            InfrastructureComponent component2 = components.get(1);
            assertThat(component2.getName()).isEqualTo("app_sa");
            assertThat(component2.getType()).isEqualTo("google_service_account");
            assertThat(component2.getProvider()).isEqualTo("GCP");
            assertThat(component2.getIdentityType()).isEqualTo(IdentityType.IDENTITY_RESOURCE);
            assertThat(component2.getId()).startsWith("google_service_account.app_sa.");
        }

        @Test
        @DisplayName("Should handle empty parsed Terraform")
        void shouldHandleEmptyParsedTerraform() {
            ParsedTerraform emptyParsed = ParsedTerraform.builder()
                .resources(List.of())
                .build();

            List<InfrastructureComponent> components = extractor.extractComponents(emptyParsed);

            assertThat(components).isEmpty();
        }

        @Test
        @DisplayName("Should handle null parsed Terraform")
        void shouldHandleNullParsedTerraform() {
            List<InfrastructureComponent> components = extractor.extractComponents(null);
            assertThat(components).isEmpty();
        }

        @Test
        @DisplayName("Should handle parsed Terraform with null resources")
        void shouldHandleParsedTerraformWithNullResources() {
            ParsedTerraform parsed = new ParsedTerraform();
            // resources is null by default

            List<InfrastructureComponent> components = extractor.extractComponents(parsed);
            assertThat(components).isEmpty();
        }

        @Test
        @DisplayName("Should generate unique IDs for components")
        void shouldGenerateUniqueIdsForComponents() {
            TerraformResource resource1 = createResource("google_compute_instance", "web_server");
            TerraformResource resource2 = createResource("google_compute_instance", "web_server");
            
            ParsedTerraform parsed = ParsedTerraform.builder()
                .resources(List.of(resource1, resource2))
                .build();

            List<InfrastructureComponent> components = extractor.extractComponents(parsed);

            assertThat(components).hasSize(2);
            assertThat(components.get(0).getId()).isNotEqualTo(components.get(1).getId());
            assertThat(components.get(0).getId()).startsWith("google_compute_instance.web_server.");
            assertThat(components.get(1).getId()).startsWith("google_compute_instance.web_server.");
        }

        @Test
        @DisplayName("Should preserve all resource properties")
        void shouldPreserveAllResourceProperties() {
            Map<String, Object> complexProperties = new HashMap<>();
            complexProperties.put("name", "test-instance");
            complexProperties.put("machine_type", "e2-medium");
            complexProperties.put("zone", "us-central1-a");
            complexProperties.put("boot_disk", Map.of(
                "initialize_params", Map.of(
                    "image", "debian-cloud/debian-11",
                    "size", 20
                )
            ));
            complexProperties.put("network_interface", List.of(
                Map.of("network", "default", "access_config", Map.of())
            ));
            complexProperties.put("tags", List.of("web", "production"));

            TerraformResource resource = createResource("google_compute_instance", "complex_vm", complexProperties);
            ParsedTerraform parsed = ParsedTerraform.builder()
                .resources(List.of(resource))
                .build();

            List<InfrastructureComponent> components = extractor.extractComponents(parsed);

            assertThat(components).hasSize(1);
            InfrastructureComponent component = components.get(0);
            assertThat(component.getProperties()).isEqualTo(complexProperties);
            assertThat(component.getProperty("name")).isEqualTo("test-instance");
            assertThat(component.getProperty("tags")).isEqualTo(List.of("web", "production"));
        }
    }

    // Helper methods
    private TerraformResource createResource(String type, String name) {
        return createResource(type, name, new HashMap<>());
    }

    private TerraformResource createResource(String type, String name, Map<String, Object> arguments) {
        return TerraformResource.builder()
                .type(type)
                .name(name)
                .arguments(arguments)
                .build();
    }
}