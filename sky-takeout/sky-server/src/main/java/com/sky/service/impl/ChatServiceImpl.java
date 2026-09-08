package com.sky.service.impl;

import com.sky.entity.ChatMessage;
import com.sky.entity.ChatSession;
import com.sky.mapper.ChatMessageMapper;
import com.sky.mapper.ChatSessionMapper;
import com.sky.service.ChatService;
import com.sky.service.MemoryService;
import com.sky.vo.ChatMessageVO;
import com.sky.vo.ChatSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {


    private final ChatSessionMapper sessionMapper;

    private final MemoryService memoryService;

    private final ChatMessageMapper messageMapper;


    @Override
    public Long createSession(Long userId){

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("新聊天");
        LocalDateTime now = LocalDateTime.now();
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);
        return session.getId();
    }


    @Override
    public void saveMessage(Long sessionId, Long userId, String role, String content){

        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }


    @Override
    public List<ChatMessageVO> history(Long sessionId, Long userId) {

        // 查询会话
        ChatSession session = sessionMapper.selectById(sessionId);

        // 用户权限校验
        if(session == null || !session.getUserId().equals(userId)){
            throw new RuntimeException("无权查看该会话");
        }

        // 查询消息
        List<ChatMessage> messages = messageMapper.listBySessionId(sessionId);
        return convert(messages);
    }

    /**
     * 将数据库聊天消息转换成VO
     *
     * Entity:
     *      ChatMessage
     *
     * VO:
     *      ChatMessageVO
     */
    private List<ChatMessageVO> convert(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> {
                    ChatMessageVO vo = new ChatMessageVO();
                    // 用户角色
                    vo.setRole(message.getRole());
                    // 消息内容
                    vo.setContent(message.getContent());
                    // 创建时间
                    vo.setCreateTime(message.getCreateTime());
                    return vo;
                })
                .toList();
    }


//    更新标题
    @Override
    public void updateTitle(Long sessionId, String title) {
        if (sessionId == null) {
            throw new RuntimeException("会话ID不能为空");
        }
        if (title == null || title.trim().isEmpty()) {
            return;
        }
        sessionMapper.updateTitle(sessionId, title, LocalDateTime.now());
    }


    @Override
    public void touchSession(Long sessionId) {

        if (sessionId == null) {
            return;
        }

        sessionMapper.updateTime(
                sessionId,
                LocalDateTime.now()
        );
    }


//    手动更新标题
    @Override
    public void updateTitle(Long sessionId, Long userId, String title) {
        if (sessionId == null) {
            throw new RuntimeException("会话ID不能为空");
        }

        if (title == null || title.trim().isEmpty()) {
            throw new RuntimeException("会话标题不能为空");
        }

        String finalTitle = title.trim();

        // 防止标题过长
        if (finalTitle.length() > 30) {
            throw new RuntimeException("会话标题不能超过30个字符");
        }

        int rows = sessionMapper.updateTitleByUser(sessionId, userId, finalTitle);

        /*
         * 更新不到数据通常只有两种情况：
         * 1. session不存在
         * 2. session不属于当前用户
         *
         * 对外统一处理，避免泄露其他用户会话是否存在。
         */
        if (rows == 0) {
            throw new RuntimeException("会话不存在");
        }
    }


//    查询所有会话
    @Override
    public List<ChatSessionVO> listSessions(Long userId) {

        List<ChatSession> sessions = sessionMapper.listByUserId(userId);
        return sessions.stream()
                .map(session ->
                        ChatSessionVO.builder()
                                .sessionId(session.getId())
                                .title(session.getTitle())
                                .updateTime(session.getUpdateTime())
                                .build()
                )
                .toList();
    }


}
