package com.sky.mq;

import com.alibaba.fastjson.JSONObject;
import com.sky.dto.OrderPaidMessageDTO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderMessageListenerTest {

    @Test
    void handleOrderPaidMessagePushesWebSocketNotification() {
        WebSocketServer webSocketServer = mock(WebSocketServer.class);
        OrderMessageListener listener = new OrderMessageListener(webSocketServer);
        OrderPaidMessageDTO message = OrderPaidMessageDTO.builder()
                .type(1)
                .orderId(100L)
                .orderNumber("202608310001")
                .content("订单号：202608310001")
                .build();

        listener.handleOrderPaidMessage(message);

        Map<String, Object> expectedWebsocketMessage = new HashMap<>();
        expectedWebsocketMessage.put("type", 1);
        expectedWebsocketMessage.put("orderID", 100L);
        expectedWebsocketMessage.put("content", "订单号：202608310001");
        verify(webSocketServer).sendToAllClient(JSONObject.toJSONString(expectedWebsocketMessage));
    }
}
