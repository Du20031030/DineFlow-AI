package com.sky.mq;

/**
 * RabbitMQ 交换机、队列和路由键常量。
 * <p>
 * 统一放在这里，避免生产者、消费者和配置类里散落字符串，后续改队列名时只需要改一处。
 */
public class RabbitMQConstant {

    /**
     * 订单业务交换机。
     * 订单相关事件都可以发到这个交换机，再通过 routing key 分发到不同队列。
     */
    public static final String ORDER_EXCHANGE = "sky.order.exchange";

    /**
     * 订单支付成功队列。
     * 当前消费者监听这个队列后，负责推送管理端来单提醒。
     */
    public static final String ORDER_PAID_QUEUE = "sky.order.paid.queue";

    /**
     * 订单支付成功路由键。
     */
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";

    private RabbitMQConstant() {
    }
}
