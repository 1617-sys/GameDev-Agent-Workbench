package com.example.gameworkbench.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MessagingConfiguration.class, TestConnectionFactoryConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=async",
                    "app.messaging.workflow-exchange=workflow.events.test",
                    "app.messaging.workflow-queue=workflow.run.execute.test",
                    "app.messaging.workflow-routing-key=workflow.run.requested.test",
                    "app.messaging.consumer.execution-lock-ttl-seconds=900",
                    "app.outbox.publisher.batch-size=20",
                    "app.outbox.publisher.poll-delay-ms=1000",
                    "app.outbox.publisher.claim-lease-ms=30000",
                    "app.outbox.publisher.retry-delay-ms=5000");

    @Test
    void asyncProfileProvidesJsonConverterAndDurableTopologyWithoutBrokerConnection() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MessageConverter.class);
            assertThat(context.getBean(MessageConverter.class)).isInstanceOf(Jackson2JsonMessageConverter.class);
            assertThat(context).hasSingleBean(Declarables.class);
            assertThat(context.getBean(RabbitMqInfrastructureProperties.class).workflowExchange())
                    .isEqualTo("workflow.events.test");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConnectionFactoryConfiguration {
        @Bean
        ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }
}
