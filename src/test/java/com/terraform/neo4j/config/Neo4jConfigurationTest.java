package com.terraform.neo4j.config;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic configuration tests for Neo4j setup.
 */
@SpringBootTest
@ActiveProfiles("test")
class Neo4jConfigurationTest {

    @Autowired
    private Neo4jConfig neo4jConfig;

    @Autowired
    private Driver driver;

    @Test
    void shouldLoadNeo4jConfiguration() {
        // Then
        assertThat(neo4jConfig).isNotNull();
        assertThat(driver).isNotNull();
    }
}