package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mq.OrderMessageProducer;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceImplMessagingTest {

    @Test
    void paySuccessPublishesOrderPaidMessageInsteadOfPushingWebSocketDirectly() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderMessageProducer orderMessageProducer = mock(OrderMessageProducer.class);
        WebSocketServer webSocketServer = mock(WebSocketServer.class);
        OrderServiceImpl orderService = new OrderServiceImpl();

        ReflectionTestUtils.setField(orderService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "orderMessageProducer", orderMessageProducer);
        ReflectionTestUtils.setField(orderService, "webSocketServer", webSocketServer);

        Orders paidOrder = Orders.builder()
                .id(100L)
                .number("202608310001")
                .build();
        when(orderMapper.getByNumber("202608310001")).thenReturn(paidOrder);

        orderService.paySuccess("202608310001");

        ArgumentCaptor<Orders> ordersCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).update(ordersCaptor.capture());
        assertEquals(Orders.TO_BE_CONFIRMED, ordersCaptor.getValue().getStatus());
        assertEquals(Orders.PAID, ordersCaptor.getValue().getPayStatus());

        verify(orderMessageProducer).sendOrderPaidMessage(any());
        verifyNoInteractions(webSocketServer);
    }
}
