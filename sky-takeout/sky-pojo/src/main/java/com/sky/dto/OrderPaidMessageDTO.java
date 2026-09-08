package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单支付成功后投递到消息队列的消息体。
 * <p>
 * 这个对象只描述“订单已经支付成功”这个事件本身，不直接关心后续要做什么。
 * 目前消费者会把它转换成 WebSocket 消息推送给管理端，后续也可以新增消费者做短信、
 * 日志、用户订阅消息等扩展。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidMessageDTO implements Serializable {

    /**
     * 消息类型。
     * 兼容原有 WebSocket 前端约定：1 表示来单提醒，2 表示客户催单。
     */
    private Integer type;

    /**
     * 订单 id。
     * JSON输出时保持原来 WebSocket 使用的 orderID 字段名，避免影响管理端页面解析。
     */
    @JsonProperty("orderID")
    private Long orderId;

    /**
     * 订单号，用于日志排查和后续扩展。
     */
    private String orderNumber;

    /**
     * 推送给管理端展示的提示内容。
     */
    private String content;
}
