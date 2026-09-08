package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户与Agent对话时传递的数据
 */
@Data
public class UserAgentChatDTO implements Serializable {

    /**
     * 用户发送给Agent的消息
     */
    private String message;

//    当前聊天会话id
    private Long sessionId;

    /**
     * 当前Agent会话的唯一标识
     */
    private String threadId;
}