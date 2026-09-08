package com.sky.config;

import com.sky.mq.RabbitMQConstant;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类。
 * <p>
 * 这里声明交换机、队列和绑定关系，Spring 启动后会自动向 RabbitMQ 创建这些基础设施。
 * 当前只接入“订单支付成功”事件，用来把原来同步 WebSocket 推送改为异步消息消费。
 */
@Configuration
@EnableRabbit
public class RabbitMQConfiguration {

    /**
     * 订单业务交换机。
     * 使用 DirectExchange 是因为当前按精确 routing key 投递，简单清晰。
     */
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(RabbitMQConstant.ORDER_EXCHANGE, true, false);
    }

    /**
     * 订单支付成功队列。
     * durable=true 表示队列持久化，RabbitMQ 重启后队列仍然存在。
     */
    @Bean
    public Queue orderPaidQueue() {
        return new Queue(RabbitMQConstant.ORDER_PAID_QUEUE, true);
    }

    /**
     * 绑定订单交换机和支付成功队列。
     * 生产者发送 routing key 为 order.paid 的消息时，会路由到 sky.order.paid.queue。
     */
    @Bean
    public Binding orderPaidBinding(DirectExchange orderExchange, Queue orderPaidQueue) {
        return BindingBuilder.bind(orderPaidQueue)
                .to(orderExchange)
                .with(RabbitMQConstant.ORDER_PAID_ROUTING_KEY);
    }

    /**
     * 使用 JSON 序列化消息体，便于在 RabbitMQ 管理台查看消息内容。
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
