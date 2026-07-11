package com.example.gameworkbench.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
@EnableConfigurationProperties({RabbitMqInfrastructureProperties.class, OutboxPublisherProperties.class, WorkflowConsumerProperties.class, WorkflowRetryProperties.class})
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
        TopicExchange retryExchange = new TopicExchange("workflow.retry", true, false);
        TopicExchange deadLetterExchange = new TopicExchange("workflow.dlx", true, false);
        Queue retry30s = QueueBuilder.durable("workflow.run.retry.30s").withArgument("x-message-ttl", 30000)
                .withArgument("x-dead-letter-exchange", properties.workflowExchange())
                .withArgument("x-dead-letter-routing-key", properties.workflowRoutingKey()).build();
        Queue retry5m = QueueBuilder.durable("workflow.run.retry.5m").withArgument("x-message-ttl", 300000)
                .withArgument("x-dead-letter-exchange", properties.workflowExchange())
                .withArgument("x-dead-letter-routing-key", properties.workflowRoutingKey()).build();
        Queue retry30m = QueueBuilder.durable("workflow.run.retry.30m").withArgument("x-message-ttl", 1800000)
                .withArgument("x-dead-letter-exchange", properties.workflowExchange())
                .withArgument("x-dead-letter-routing-key", properties.workflowRoutingKey()).build();
        Queue dlq = QueueBuilder.durable("workflow.run.dlq").build();
        return new Declarables(exchange, queue, binding, retryExchange, deadLetterExchange, retry30s, retry5m, retry30m, dlq,
                BindingBuilder.bind(retry30s).to(retryExchange).with("retry.30s"),
                BindingBuilder.bind(retry5m).to(retryExchange).with("retry.5m"),
                BindingBuilder.bind(retry30m).to(retryExchange).with("retry.30m"),
                BindingBuilder.bind(dlq).to(deadLetterExchange).with("workflow.run.failed"));
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
