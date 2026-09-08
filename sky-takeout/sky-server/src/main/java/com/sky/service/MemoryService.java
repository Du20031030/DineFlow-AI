package com.sky.service;

import com.sky.context.ChatMemoryContext;
import com.sky.entity.ChatMessage;

import java.util.List;

public interface MemoryService {


    /**
     * 加载当前会话上下文
     */
    ChatMemoryContext loadContext(
            Long sessionId,
            Long userId
    );


    /**
     * 保存消息
     */
    Long saveMessage(
            Long sessionId,
            Long userId,
            String role,
            String content
    );


    /**
     * 判断是否需要摘要
     */
    boolean needSummary(
            Long sessionId
    );


    /**
     * 更新摘要
     */
    void updateSummary(
            Long sessionId,
            String summary
    );

    /**
     * PostgresSaver异常时，
     * 从MySQL恢复当前消息之前的完整会话历史。
     */
    List<ChatMessage> getFallbackHistory(
            Long userId,
            Long messageId
    );

}