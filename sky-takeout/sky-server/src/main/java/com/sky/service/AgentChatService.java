package com.sky.service;

import com.sky.dto.UserAgentChatDTO;
import com.sky.vo.UserAgentChatVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentChatService {

    /**
     * 普通非流式聊天
     */
    UserAgentChatVO chat(UserAgentChatDTO dto);

    /**
     * SSE流式聊天
     */
    SseEmitter streamChat(UserAgentChatDTO dto);


    /**
     * 删除当前用户的指定会话，
     * 同时删除 LangGraph 对应的短期记忆。
     */
    void deleteSession(Long sessionId);
}