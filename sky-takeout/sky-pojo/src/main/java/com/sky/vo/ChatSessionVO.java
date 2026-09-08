package com.sky.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户历史会话列表
 */
@Data
@Builder
public class ChatSessionVO {

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 最后活跃时间
     */
    private LocalDateTime updateTime;
}