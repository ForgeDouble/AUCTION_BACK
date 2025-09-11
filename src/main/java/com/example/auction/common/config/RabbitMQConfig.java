package com.example.auction.common.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



@Configuration
public class RabbitMQConfig {

    // 큐 생성
    @Bean
    public Queue bidQueue() {
        return new Queue("bid-queue", true); // durable = true
    }

    // 교환기 생성
    @Bean
    public DirectExchange bidExchange() {
        return new DirectExchange("bid-exchange");
    }

    // 큐와 교환기 바인딩
    @Bean
    public Binding binding(Queue bidQueue, DirectExchange bidExchange) {
        return BindingBuilder.bind(bidQueue).to(bidExchange).with("bid-routing-key");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter());
        return template;
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }


}
