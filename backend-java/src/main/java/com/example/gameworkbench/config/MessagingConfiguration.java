package com.example.gameworkbench.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * R3 transport infrastructure: declares the workflow topology and JSON converter.
 * Consumer and workflow business behavior remain outside this configuration.
 */
@Configuration
@Profile("async")
@EnableConfigurationProperties({RabbitMqInfrastructureProperties.class, OutboxPublisherProperties.class, WorkflowConsumerProperties.class})
@EnableScheduling
public class MessagingConfiguration {

    @Bean
    public MessageConverter rabbitMqMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Declarables workflowMessagingTopology(RabbitMqInfrastructureProperties properties) {
        TopicExchange exchange = new TopicExchange(properties.workflowExchange(), true, false);
        Queue queue = new Queue(properties.workflowQueue(), true);
        Binding binding = BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.workflowRoutingKey());
        return new Declarables(exchange, queue, binding);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory workflowRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter rabbitMqMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setMessageConverter(rabbitMqMessageConverter);
        return factory;
    }
}
