package br.com.ricarte.assinaflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public Declarables paymentsDeclarables(
            @Value("${app.rabbitmq.payments.exchange:payments.exchange}") String exchangeName,
            @Value("${app.rabbitmq.payments.queue:payments.charge}") String queueName,
            @Value("${app.rabbitmq.payments.routingKey:payments.charge}") String routingKey
    ) {
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);
        Queue queue = QueueBuilder.durable(queueName).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        return new Declarables(exchange, queue, binding);
    }
}
