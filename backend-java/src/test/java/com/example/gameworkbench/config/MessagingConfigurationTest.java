package com.example.gameworkbench.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MessagingConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=async",
                    "app.messaging.workflow-exchange=workflow.events.test",
                    "app.messaging.workflow-queue=workflow.run.execute.test",
                    "app.messaging.workflow-routing-key=workflow.run.requested.test");

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
}
