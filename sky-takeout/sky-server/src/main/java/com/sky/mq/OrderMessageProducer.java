package com.sky.mq;

import com.sky.dto.OrderPaidMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单消息生产者。
 * <p>
 * 业务层只需要调用这里发布“发生了什么事件”，不用关心消息会被哪个消费者处理。
 * 这样订单支付成功逻辑和 WebSocket 通知逻辑就解耦了。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送订单支付成功消息。
     * <p>
     * 发送失败时只记录日志，不向上抛出异常，避免 RabbitMQ 短暂不可用影响支付成功后的订单状态更新。
     *
     * @param message 订单支付成功事件消息
     */
    public void sendOrderPaidMessage(OrderPaidMessageDTO message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConstant.ORDER_EXCHANGE,
                    RabbitMQConstant.ORDER_PAID_ROUTING_KEY,
                    message
            );
            log.info("订单支付成功消息已发送到RabbitMQ：{}", message);
        } catch (AmqpException ex) {
            log.error("订单支付成功消息发送失败，订单状态已更新但管理端来单提醒可能延迟，message={}", message, ex);
        }
    }
}
