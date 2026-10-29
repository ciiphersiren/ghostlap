package com.ghostlap.player;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a real Postgres in Docker for the tests. @ServiceConnection points the datasource at it,
 * replacing spring.datasource.* from application.properties.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Same major version as infra/docker-compose.yml.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
}
