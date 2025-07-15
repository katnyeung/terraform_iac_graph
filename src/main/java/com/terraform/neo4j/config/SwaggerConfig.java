package com.terraform.neo4j.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Terraform Neo4j Parser API")
                        .version("1.0.0")
                        .description("API for parsing Terraform files and creating Neo4j graph representations")
                        .contact(new Contact()
                                .name("Terraform Neo4j Parser Team")
                                .email("support@terraform-parser.com")));
    }
}