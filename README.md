# Terraform Neo4j Parser

A Spring Boot application that parses Terraform configurations and creates intelligent graph representations in Neo4j for infrastructure analysis and visualization.

## 🚀 Overview

This project transforms Terraform Infrastructure as Code (IaC) into structured graph data, enabling:

- **Parse Terraform configurations** from text content or zip file uploads
- **Extract infrastructure components** with provider detection and resource classification
- **Store in Neo4j graph database** with intelligent property serialization
- **REST API endpoints** for easy integration with other tools
- **Identity resource detection** for security and compliance analysis

## 🔑 Key Features

### ✅ Current Implementation (v1.0)
- **Terraform Parsing**: Parse `.tf` files using HCL4j with comprehensive error handling
- **REST API Endpoints**: 
  - `POST /api/terraform/parse` - Parse Terraform content from JSON requests
  - `POST /api/terraform/parse-zip` - Upload and parse zip files containing .tf files
- **Neo4j Integration**: Store infrastructure components in Neo4j Aura with intelligent property serialization
- **Provider Detection**: Automatically detect cloud providers (AWS, GCP, Azure, Kubernetes, Helm, Docker)
- **Identity Resource Classification**: Distinguish between identity resources and regular infrastructure
- **Complex Property Handling**: Serialize nested configurations (like Helm charts) to JSON strings
- **Comprehensive Error Handling**: Proper HTTP status codes and detailed error responses
- **API Documentation**: Full Swagger/OpenAPI 3 documentation with interactive UI
- **Input Validation**: Request validation with detailed error messages
- **File Upload Support**: Handle zip file uploads with Terraform configurations

### 🔧 Recent Improvements
- **Fixed Neo4j Property Serialization**: Resolved issues with complex nested objects (like Helm chart configurations)
- **Enhanced Error Handling**: Better handling of parsing errors and validation failures
- **Neo4j Aura Support**: Configured for cloud Neo4j instances with secure connections
- **Improved Property Storage**: Complex properties are now serialized to JSON strings for Neo4j compatibility

## 🛠️ Technology Stack

- **Backend**: Spring Boot 3.2.0 (Java 17+)
- **Parser**: HCL4j for Terraform configuration parsing
- **Database**: Neo4j Aura (Cloud) / Neo4j 5.0+ for graph storage
- **Build Tool**: Maven 3.8+
- **API Documentation**: Swagger/OpenAPI 3 with SpringDoc
- **Testing**: JUnit 5, Mockito, Spring Boot Test
- **Validation**: Jakarta Bean Validation
- **JSON Processing**: Jackson for complex property serialization

## 📋 Prerequisites

- Java 17+
- Neo4j Aura account (or Neo4j 5.0+ local installation)
- Maven 3.8+

## 🚦 Getting Started

### 1. Clone the repository
```bash
git clone https://github.com/katnyeung/terraform-neo4j-parser.git
cd terraform-neo4j-parser
```

### 2. Configure Neo4j Connection
Update `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: terraform-neo4j-parser
  
  neo4j:
    # For Neo4j Aura (recommended)
    uri: neo4j+s://YOUR_INSTANCE_ID.databases.neo4j.io
    authentication:
      username: neo4j
      password: your-aura-password
    
    # For local Neo4j installation
    # uri: bolt://localhost:7687
    # authentication:
    #   username: neo4j
    #   password: your-local-password
    
  data:
    neo4j:
      database: neo4j

server:
  port: 8080

# API Documentation
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true

logging:
  level:
    com.terraform.neo4j: DEBUG
    org.neo4j: INFO
```

### 3. Build and run
```bash
mvn clean install
mvn spring-boot:run
```

### 4. Access the API Documentation
Navigate to `http://localhost:8080/swagger-ui.html` to access the interactive API documentation.

### 5. Test the API Endpoints

#### Parse Terraform Content
```bash
curl -X POST http://localhost:8080/api/terraform/parse \
  -H "Content-Type: application/json" \
  -d '{
    "content": "resource \"aws_instance\" \"web\" {\n  ami = \"ami-12345\"\n  instance_type = \"t2.micro\"\n}",
    "description": "Test infrastructure"
  }'
```

#### Upload Terraform Zip File
```bash
curl -X POST http://localhost:8080/api/terraform/parse-zip \
  -F "file=@terraform-configs.zip"
```

## 💡 How It Works

### 1. Terraform Parsing
The application uses HCL4j to parse Terraform configurations:

```java
@Service
public class TerraformParser {
    public ParsedTerraform parse(List<TerraformFile> files) {
        // Parse each .tf file using HCL4j
        // Extract resources, variables, outputs, and providers
        // Handle parsing errors gracefully
        return parsedResult;
    }
}
```

### 2. Resource Extraction and Classification
```java
@Component
public class BasicResourceExtractor {
    public List<InfrastructureComponent> extractComponents(ParsedTerraform parsed) {
        // Convert Terraform resources to infrastructure components
        // Detect cloud providers (AWS, GCP, Azure, etc.)
        // Classify identity resources vs regular resources
        // Generate unique IDs for each component
        return components;
    }
}
```

### 3. Neo4j Graph Mapping
```java
@Service
public class SimpleNeo4jMapper {
    public void mapToGraph(List<InfrastructureComponent> components) {
        // Serialize complex properties to JSON strings
        // Create Resource nodes in Neo4j
        // Handle nested configurations (like Helm charts)
        // Set up indexes for performance
    }
}
```

## 📊 API Response Examples

### Successful Parse Response
```json
{
  "success": true,
  "message": "Successfully parsed and mapped 5 resources to Neo4j graph",
  "resourceCount": 5,
  "identityResourceCount": 2,
  "regularResourceCount": 3,
  "providersDetected": ["AWS", "GCP"],
  "resourceTypeSummary": [
    {
      "resourceType": "aws_instance",
      "count": 2
    },
    {
      "resourceType": "google_service_account",
      "count": 1
    }
  ],
  "timestamp": "2024-07-15T22:30:00"
}
```

### Error Response
```json
{
  "status": 422,
  "error": "PARSING_ERROR",
  "message": "Terraform configuration contains parsing errors",
  "details": [
    "Invalid HCL syntax at line 5",
    "Unknown resource type at line 12"
  ],
  "path": "/api/terraform/parse",
  "timestamp": "2024-07-15T22:30:00"
}
```

## 🏗️ Architecture

```
┌─────────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│   REST API          │────▶│   HCL4j Parser      │────▶│   Resource          │
│   (Spring Boot)     │     │   (Terraform → AST) │     │   Extractor         │
└─────────────────────┘     └─────────────────────┘     └─────────────────────┘
           │                                                        │
           │                                                        ▼
           │                 ┌─────────────────────┐     ┌─────────────────────┐
           │                 │   Error Handler     │     │   Neo4j Mapper      │
           └────────────────▶│   (Global)          │     │   (Graph Storage)   │
                             └─────────────────────┘     └─────────────────────┘
                                                                    │
                                                                    ▼
                                                         ┌─────────────────────┐
                                                         │   Neo4j Aura        │
                                                         │   (Cloud Database)  │
                                                         └─────────────────────┘
```

## 🔧 Supported Terraform Resources

### Cloud Providers
- **AWS**: `aws_*` resources (EC2, RDS, Lambda, etc.)
- **Google Cloud**: `google_*` resources (Compute Engine, Cloud SQL, etc.)
- **Azure**: `azurerm_*` resources (Virtual Machines, Storage, etc.)

### Container Orchestration
- **Kubernetes**: `kubernetes_*` resources
- **Helm**: `helm_release` resources (with complex property handling)
- **Docker**: `docker_*` resources

### Identity Resources Detection
The system automatically identifies identity-related resources:
- AWS IAM roles, users, policies
- Google Cloud service accounts
- Azure managed identities
- Kubernetes service accounts

## 🐛 Troubleshooting

### Common Issues

#### Neo4j Connection Errors
```
Error: Failed to connect to Neo4j
```
**Solution**: Check your Neo4j Aura URI and credentials in `application.yml`

#### Complex Property Serialization Errors
```
Error: Property values can only be of primitive types
```
**Solution**: This has been fixed in the latest version. Complex properties are now serialized to JSON strings.

#### Terraform Parsing Errors
```
Error: HCL parsing error
```
**Solution**: Validate your Terraform syntax. The API will return detailed error messages.

## 🚀 Future Roadmap

### Phase 2: LLM-Powered Intelligence Layer
- **Relationship Inference**: Use LLM to discover implicit dependencies between resources
- **Context Understanding**: LLM interprets infrastructure intent and business logic
- **Pattern Recognition**: Identify architectural patterns and anti-patterns using AI
- **Security Analysis**: LLM-powered security misconfiguration detection

### Phase 3: Pre-Deployment Simulation
- **LLM Infrastructure Simulation**: Simulate infrastructure behavior before `terraform apply`
  - Predict resource interactions and potential bottlenecks
  - Estimate performance characteristics and scaling behavior
  - Identify potential failure points and cascading issues
  - Generate "what-if" scenarios for different workload patterns
- **Cost Prediction**: LLM estimates infrastructure costs and suggests optimizations
- **Risk Assessment**: Analyze deployment risks and suggest mitigation strategies

### Phase 4: LLM Benchmark & Validation System
- **Multi-Model Comparison**: Benchmark different LLMs (GPT-4, Claude, DeepSeek, Llama) for infrastructure analysis
- **Accuracy Metrics**: Measure precision/recall for relationship detection and pattern recognition
- **Ground Truth Validation**: Test against expert-curated infrastructure scenarios
- **Performance Benchmarking**: Compare speed, cost, and consistency across LLM providers
- **Model Selection**: Automatically choose the best LLM for specific infrastructure analysis tasks

### Phase 5: Advanced Features
- **Conversational Infrastructure**: Chat with your infrastructure using natural language
- **Multi-IaC Support**: Unified analysis across Terraform, CloudFormation, Pulumi
- **Interactive Visualization**: LLM-enhanced graph visualization with natural language queries
- **Automated Remediation**: LLM generates Terraform code to fix identified issues
- **Team Knowledge Base**: LLM learns from your organization's infrastructure patterns

### Phase 6: Production Integration
- **CI/CD Integration**: Pre-deployment simulation in your pipeline
- **Real-time Monitoring**: Compare LLM predictions with actual infrastructure behavior
- **Continuous Learning**: Improve LLM accuracy based on production feedback
- **Multi-Cloud Optimization**: LLM suggests optimal resource placement across cloud providers

## 🤝 Contributing

We welcome contributions! Areas where you can help:

- **Parser Improvements**: Better handling of complex Terraform configurations
- **Provider Support**: Add support for more cloud providers
- **Testing**: Improve test coverage and add integration tests
- **Documentation**: Improve API documentation and examples

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [HCL4j](https://github.com/bertramlabs/hcl4j) team for the excellent Terraform parser
- [Neo4j](https://neo4j.com/) for the powerful graph database
- [Spring Boot](https://spring.io/projects/spring-boot) community for the robust framework

## 📧 Contact

- **Project Lead**: Wing YEUNG
- **Email**: katnyeung@gmail.com
- **GitHub**: http://github.com/katnyeung

---

**Note**: This project focuses on parsing and storing Terraform configurations in Neo4j. It provides a solid foundation for building more advanced infrastructure analysis tools.