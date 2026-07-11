package com.example.gameworkbench.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * R3 infrastructure only: declares the workflow transport and JSON converter.
 * No listener, publisher, or workflow business behavior is registered here.
 */
@Configuration
@Profile("async")
@EnableConfigurationProperties(RabbitMqInfrastructureProperties.class)
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
}
