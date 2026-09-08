package com.sky.service;

import com.sky.entity.ChatMessage;
import com.sky.vo.ChatMessageVO;
import com.sky.vo.ChatSessionVO;

import java.util.List;

public interface ChatService {


//    创建会话
    Long createSession(Long userId);


//    保存历史消息
    void saveMessage(Long sessionId, Long userId, String role, String content);

    /**
     * 查询聊天历史
     *
     * @param sessionId 会话id
     * @param userId 当前用户id
     */
    List<ChatMessageVO> history(Long sessionId, Long userId);

    /**
     * 更新会话标题
     *
     * @param sessionId 会话ID
     * @param title     新标题
     */
    void updateTitle(Long sessionId, String title);


    /**
     * 更新会话最后活跃时间
     */
    void touchSession(Long sessionId);


    /**
     * 用户手动修改会话标题
     */
    void updateTitle(Long sessionId, Long userId, String title);


    /**
     * 查询当前用户的历史会话列表
     */
    List<ChatSessionVO> listSessions(Long userId);

}