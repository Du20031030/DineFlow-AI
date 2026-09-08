package com.sky.mq;

import com.alibaba.fastjson.JSONObject;
import com.sky.dto.OrderPaidMessageDTO;
import com.sky.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单消息消费者。
 * <p>
 * 当前消费者负责监听“订单已支付”消息，然后给管理端浏览器推送来单提醒。
 * 后续如果要加短信、邮件或订单事件日志，可以新建消费者监听同一个事件，不需要改订单主流程。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMessageListener {

    private final WebSocketServer webSocketServer;

    /**
     * 消费订单支付成功消息。
     * <p>
     * RabbitMQ 收到 order.paid 消息后会投递到 sky.order.paid.queue，
     * 这个方法监听该队列并执行原来的 WebSocket 推送逻辑。
     *
     * @param message 订单支付成功事件消息
     */
    @RabbitListener(queues = RabbitMQConstant.ORDER_PAID_QUEUE)
    public void handleOrderPaidMessage(OrderPaidMessageDTO message) {
        log.info("收到订单支付成功消息：{}", message);

        // 管理端原有WebSocket消息使用orderID字段名，这里保持兼容，避免前端解析受影响。
        Map<String, Object> websocketMessage = new HashMap<>();
        websocketMessage.put("type", message.getType());
        websocketMessage.put("orderID", message.getOrderId());
        websocketMessage.put("content", message.getContent());

        String json = JSONObject.toJSONString(websocketMessage);
        webSocketServer.sendToAllClient(json);
    }
}
