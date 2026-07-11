package com.example.gameworkbench.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real-dependency regression harness. It deliberately uses JDBC and the Rabbit client rather than mocks so that
 * concurrent idempotency, durable outbox intent, broker delivery, and atomic execution claims are observable.
 */
@Testcontainers(disabledWithoutDocker = true)
class AsyncWorkflowIntegrationHarnessIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("workflow_harness").withUsername("test_user").withPassword("test-only-database-password");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    private final String namespace = "r3_harness_" + UUID.randomUUID().toString().replace("-", "");

    @AfterEach
    void cleanUp() throws Exception {
        try (java.sql.Connection connection = sql(); Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists " + namespace + "_claim");
            statement.execute("drop table if exists " + namespace + "_outbox");
            statement.execute("drop table if exists " + namespace + "_run");
        }
    }

    @Test
    void tenConcurrentIdempotentSubmissionsCreateOneRunAndOneDurableOutboxIntent() throws Exception {
        createSubmissionTables();
        assertRedisReachable();
        String runUuid = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) futures.add(pool.submit(() -> submitOnce(ready, start, runUuid)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(count(namespace + "_run")).as("workflow run records").isEqualTo(1);
        assertThat(count(namespace + "_outbox")).as("durable outbox intents").isEqualTo(1);
    }

    @Test
    void duplicateBrokerDeliveryWithConcurrentConsumersClaimsOneEffectiveExecution() throws Exception {
        createClaimTable();
        String exchange = namespace + ".exchange", queue = namespace + ".queue", routingKey = "workflow.run.requested";
        try (Connection connection = rabbit(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, "direct", true);
            channel.queueDeclare(queue, true, false, true, null);
            channel.queueBind(queue, exchange, routingKey);
            byte[] body = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            channel.basicPublish(exchange, routingKey, null, body);
            channel.basicPublish(exchange, routingKey, null, body);
        }
        AtomicInteger effectiveExecutions = new AtomicInteger();
        CountDownLatch deliveries = new CountDownLatch(2);
        ExecutorService consumers = Executors.newFixedThreadPool(2);
        try {
            consumers.submit(() -> consumeOnce(queue, effectiveExecutions, deliveries));
            consumers.submit(() -> consumeOnce(queue, effectiveExecutions, deliveries));
            await(deliveries, "broker deliveries", queue);
        } finally { consumers.shutdownNow(); }
        assertThat(effectiveExecutions.get()).as("effective Runner/Agent claims").isEqualTo(1);
        assertThat(count(namespace + "_claim")).as("durable execution claims").isEqualTo(1);
    }

    private void submitOnce(CountDownLatch ready, CountDownLatch start, String runUuid) {
        try {
            ready.countDown(); start.await(5, TimeUnit.SECONDS);
            try (java.sql.Connection connection = sql()) {
                connection.setAutoCommit(false);
                try (PreparedStatement run = connection.prepareStatement("insert ignore into " + namespace + "_run values (?, ?)");
                     PreparedStatement outbox = connection.prepareStatement("insert into " + namespace + "_outbox values (?, ?)") ) {
                    run.setString(1, "same-idempotency-key"); run.setString(2, runUuid);
                    if (run.executeUpdate() == 1) { outbox.setString(1, runUuid); outbox.setString(2, "PENDING"); outbox.executeUpdate(); }
                    connection.commit();
                }
            }
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void consumeOnce(String queue, AtomicInteger executions, CountDownLatch deliveries) {
        try (Connection connection = rabbit(); Channel channel = connection.createChannel()) {
            Instant deadline = Instant.now().plus(Duration.ofSeconds(8));
            while (Instant.now().isBefore(deadline)) {
                var delivery = channel.basicGet(queue, false);
                if (delivery == null) { Thread.onSpinWait(); continue; }
                try (java.sql.Connection sql = sql(); PreparedStatement claim = sql.prepareStatement("insert ignore into " + namespace + "_claim values (?)")) {
                    claim.setString(1, new String(delivery.getBody(), StandardCharsets.UTF_8));
                    if (claim.executeUpdate() == 1) executions.incrementAndGet();
                }
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                deliveries.countDown();
                return;
            }
            throw new AssertionError("Timed out receiving message from " + queue);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void createSubmissionTables() throws Exception {
        try (java.sql.Connection connection = sql(); Statement statement = connection.createStatement()) {
            statement.execute("create table " + namespace + "_run (idempotency_key varchar(128) primary key, run_uuid varchar(36) not null)");
            statement.execute("create table " + namespace + "_outbox (run_uuid varchar(36) primary key, status varchar(20) not null)");
        }
    }
    private void createClaimTable() throws Exception { try (java.sql.Connection connection = sql(); Statement statement = connection.createStatement()) { statement.execute("create table " + namespace + "_claim (run_uuid varchar(36) primary key)"); } }
    private long count(String table) throws Exception { try (java.sql.Connection connection = sql(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("select count(*) from " + table)) { rows.next(); return rows.getLong(1); } }
    private java.sql.Connection sql() throws Exception { return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); }
    private Connection rabbit() throws Exception { ConnectionFactory factory = new ConnectionFactory(); factory.setUri(RABBIT.getAmqpUrl()); return factory.newConnection(); }
    private void assertRedisReachable() throws Exception { try (Socket socket = new Socket(REDIS.getHost(), REDIS.getMappedPort(6379))) { assertThat(socket.isConnected()).isTrue(); } }
    private void await(CountDownLatch latch, String description, String evidence) throws InterruptedException { assertThat(latch.await(10, TimeUnit.SECONDS)).as("Timed out waiting for %s; evidence=%s", description, evidence).isTrue(); }
}
