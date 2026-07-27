package com.miaotong.doc.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置(OCR AI 改造 v3:OCR 异步任务)
 *
 * 队列设计:
 *   - 队列: pdf.ocr.task (持久化)
 *   - 路由: direct exchange pdf.ocr.exchange
 *   - 路由键: ocr.task
 *
 * 消息载荷: PdfOcrTaskMessage (JSON 序列化)
 */
@Configuration
public class RabbitMqConfig {

    public static final String OCR_QUEUE = "pdf.ocr.task";
    public static final String OCR_EXCHANGE = "pdf.ocr.exchange";
    public static final String OCR_ROUTING_KEY = "ocr.task";

    @Bean
    public Queue pdfOcrTaskQueue() {
        // 持久化队列
        return new Queue(OCR_QUEUE, true);
    }

    @Bean
    public DirectExchange pdfOcrExchange() {
        return new DirectExchange(OCR_EXCHANGE, true, false);
    }

    @Bean
    public Binding pdfOcrBinding(Queue pdfOcrTaskQueue, DirectExchange pdfOcrExchange) {
        return BindingBuilder.bind(pdfOcrTaskQueue).to(pdfOcrExchange).with(OCR_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}