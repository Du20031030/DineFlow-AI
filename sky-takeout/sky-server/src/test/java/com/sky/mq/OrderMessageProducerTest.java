package com.sky.mq;

import com.sky.dto.OrderPaidMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderMessageProducerTest {

    @Test
    void sendOrderPaidMessageUsesOrderExchangeAndPaidRoutingKey() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OrderMessageProducer producer = new OrderMessageProducer(rabbitTemplate);
        OrderPaidMessageDTO message = OrderPaidMessageDTO.builder()
                .type(1)
                .orderId(100L)
                .orderNumber("202608310001")
                .content("订单号：202608310001")
                .build();

        producer.sendOrderPaidMessage(message);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConstant.ORDER_EXCHANGE,
                RabbitMQConstant.ORDER_PAID_ROUTING_KEY,
                message
        );
    }
}
