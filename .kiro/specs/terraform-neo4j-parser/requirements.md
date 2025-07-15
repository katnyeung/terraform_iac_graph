# Requirements Document

## Introduction

This feature will enhance the existing Terraform Infrastructure Simulator by creating a comprehensive parser that transforms Terraform configurations into a detailed Neo4j graph database. The focus is on mapping each Terraform component as nodes, establishing relationships between components (like GCP Pub/Sub → GKE → BigQuery), tracking service account associations, and storing configuration properties. This will provide users with clear visibility into which service accounts are linked to which services and how components interconnect.

## Requirements

### Requirement 1

**User Story:** As a DevOps engineer, I want to parse Terraform files and automatically create Neo4j nodes for each infrastructure component, so that I can visualize my entire infrastructure as a connected graph.

#### Acceptance Criteria

1. WHEN a Terraform file is uploaded THEN the system SHALL parse all resource blocks and create corresponding Neo4j nodes
2. WHEN creating nodes THEN the system SHALL include resource type, name, and all configuration properties as node attributes
3. WHEN parsing resources THEN the system SHALL support major cloud providers (GCP, AWS, Azure) resource types
4. IF a resource has nested configuration blocks THEN the system SHALL flatten and store them as node properties
5. WHEN parsing completes THEN the system SHALL provide a summary of created nodes and their types

### Requirement 2

**User Story:** As a cloud architect, I want to see explicit relationships between infrastructure components (like Pub/Sub → GKE → BigQuery), so that I can understand data flow and dependencies in my architecture.

#### Acceptance Criteria

1. WHEN parsing Terraform resources THEN the system SHALL identify explicit dependencies through resource references
2. WHEN a resource references another resource THEN the system SHALL create a directed relationship edge in Neo4j
3. WHEN creating relationships THEN the system SHALL label edges with relationship types (CONNECTS_TO, DEPENDS_ON, ROUTES_TO, etc.)
4. IF resources share network configurations THEN the system SHALL create NETWORK_CONNECTED relationships
5. WHEN resources are in the same project/resource group THEN the system SHALL create BELONGS_TO relationships
6. WHEN parsing data sources THEN the system SHALL create DATA_SOURCE relationships to referenced resources

### Requirement 3

**User Story:** As a security engineer, I want to track all service accounts and their associations with infrastructure components, so that I can audit permissions and access patterns across my infrastructure.

#### Acceptance Criteria

1. WHEN parsing Terraform files THEN the system SHALL identify all service account resources and create dedicated SA nodes
2. WHEN a resource uses a service account THEN the system SHALL create a USES_SERVICE_ACCOUNT relationship
3. WHEN service accounts have IAM bindings THEN the system SHALL create HAS_PERMISSION relationships with role details
4. IF service accounts are referenced in multiple resources THEN the system SHALL show all connections clearly
5. WHEN displaying service account relationships THEN the system SHALL include permission scopes and roles as edge properties
6. WHEN a service account is impersonated THEN the system SHALL create IMPERSONATES relationships

### Requirement 4

**User Story:** As a platform engineer, I want to store all Terraform resource configurations as node properties in Neo4j, so that I can query and analyze configuration patterns across my infrastructure.

#### Acceptance Criteria

1. WHEN creating Neo4j nodes THEN the system SHALL store all Terraform resource arguments as node properties
2. WHEN configuration values are complex objects THEN the system SHALL serialize them as JSON strings in properties
3. WHEN resources have tags or labels THEN the system SHALL store them as searchable node properties
4. IF configuration contains sensitive values THEN the system SHALL mask or exclude them from storage
5. WHEN storing properties THEN the system SHALL maintain data types (strings, numbers, booleans) where possible
6. WHEN resources have computed values THEN the system SHALL mark them as computed in the properties

### Requirement 5

**User Story:** As a developer, I want an interactive simulator that shows how data flows through my infrastructure components, so that I can understand system behavior and identify potential bottlenecks.

#### Acceptance Criteria

1. WHEN the Neo4j graph is populated THEN the system SHALL provide a simulation interface
2. WHEN starting a simulation THEN the user SHALL be able to select entry points (like Pub/Sub topics or API endpoints)
3. WHEN simulation runs THEN the system SHALL trace data flow through connected components
4. IF bottlenecks are detected THEN the system SHALL highlight affected nodes and relationships
5. WHEN simulation completes THEN the system SHALL provide performance metrics and recommendations
6. WHEN viewing results THEN the user SHALL see which service accounts are involved in each data flow path

### Requirement 6

**User Story:** As a cloud operations team member, I want to easily identify which service accounts are connected to which services through a visual interface, so that I can quickly troubleshoot access issues and audit security configurations.

#### Acceptance Criteria

1. WHEN viewing the Neo4j graph THEN the system SHALL provide filtering options for service accounts
2. WHEN selecting a service account THEN the system SHALL highlight all connected resources
3. WHEN selecting a resource THEN the system SHALL show all associated service accounts
4. IF service accounts have multiple roles THEN the system SHALL display role hierarchies clearly
5. WHEN analyzing connections THEN the system SHALL provide shortest path queries between SAs and resources
6. WHEN exporting data THEN the system SHALL generate service account audit reports