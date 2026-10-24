package com.checkout.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.checkout.common.Channels;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class QueueConfig {

    @Bean
    public Queue authorizeDlq() {
        return QueueBuilder.durable(Channels.AUTHORIZE_DLQ).build();
    }

    @Bean
    @DependsOn("authorizeDlq")
    public Queue authorizeQueue() {
        return QueueBuilder.durable(Channels.AUTHORIZE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(Channels.AUTHORIZE_DLQ)
                .build();
    }

    @Bean
    public Queue allocateDlq() {
        return QueueBuilder.durable(Channels.ALLOCATE_DLQ).build();
    }

    @Bean
    @DependsOn("allocateDlq")
    public Queue allocateQueue() {
        return QueueBuilder.durable(Channels.ALLOCATE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(Channels.ALLOCATE_DLQ)
                .build();
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
