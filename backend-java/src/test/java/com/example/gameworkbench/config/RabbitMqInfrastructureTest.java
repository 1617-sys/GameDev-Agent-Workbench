package com.example.gameworkbench.config;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real dependency smoke test. Testcontainers skips the class when Docker is unavailable;
 * it never falls back to a mocked broker, database, or cache.
 */
@Testcontainers(disabledWithoutDocker = true)
class RabbitMqInfrastructureTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("gamedev_agent_workbench_test")
            .withUsername("test_user")
            .withPassword("test-only-database-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    @Test
    void startsRabbitMqMysqlAndRedisForTheR3IntegrationHarness() throws Exception {
        assertThat(MYSQL.createConnection("").isValid(2)).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(RABBITMQ.isRunning()).isTrue();
        assertThat(RABBITMQ.getAmqpPort()).isPositive();
    }
}
