package com.terraform.neo4j.config;

import com.terraform.neo4j.service.SimpleNeo4jMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Database initializer that runs on application startup.
 * Sets up necessary constraints and indexes in Neo4j database.
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final SimpleNeo4jMapper neo4jMapper;

    public DatabaseInitializer(SimpleNeo4jMapper neo4jMapper) {
        this.neo4jMapper = neo4jMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing Neo4j database on application startup");
        
        try {
            neo4jMapper.initializeDatabase();
            logger.info("Database initialization completed successfully");
        } catch (Exception e) {
            logger.error("Database initialization failed", e);
            // Don't fail the application startup, just log the error
        }
    }
}