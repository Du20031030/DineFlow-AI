package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户Agent聊天返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAgentChatVO implements Serializable {

    /**
     * 当前Agent会话ID
     */
    private String threadId;

    /**
     * Agent返回给用户的回答
     */
    private String content;

    private Long sessionId;
}