package com.sky.service.impl;

import com.sky.context.ChatMemoryContext;
import com.sky.entity.ChatMessage;
import com.sky.entity.ChatSession;
import com.sky.mapper.ChatMessageMapper;
import com.sky.mapper.ChatSessionMapper;
import com.sky.service.MemoryService;
import com.sky.vo.ChatMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service("chatMemoryService")
@RequiredArgsConstructor
@Slf4j
public class MemoryServiceImpl implements MemoryService {


    private final ChatSessionMapper sessionMapper;

    private final ChatMessageMapper messageMapper;


    @Override
    public ChatMemoryContext loadContext(Long sessionId, Long userId) {
        // 查询当前会话
        ChatSession session = sessionMapper.selectById(sessionId);

        // 校验用户权限
        if(session == null || !session.getUserId().equals(userId)){
            throw new RuntimeException("会话不存在");
        }

        // 获取历史摘要
        String summary = session.getSummary();

        // 获取最近20条消息
        List<ChatMessage> messages = messageMapper.selectRecentMessages(sessionId, 20);

        if(messages == null){
            messages = new ArrayList<>();

        }

        // 数据库倒序查询，需要恢复聊天顺序
        Collections.reverse(messages);

        return ChatMemoryContext.builder()
                .summary(summary)
                .messages(convert(messages))
                .build();

    }



//    保存对话消息
    @Override
    public Long saveMessage(Long sessionId, Long userId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(
                LocalDateTime.now()
        );
        messageMapper.insert(message);
        return message.getId();
    }



    @Override
    public boolean needSummary(Long sessionId) {
        Integer count = messageMapper.countBySessionId(sessionId);
        return count != null && count >= 50;

    }



    @Override
    public void updateSummary(Long sessionId, String summary) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setSummary(summary);
        session.setUpdateTime(LocalDateTime.now()
        );


        sessionMapper.updateSummary(session);

    }


    /**
     * 将数据库消息对象转换成前端/Agent使用的VO对象
     *
     * ChatMessage:
     *      数据库实体
     *
     * ChatMessageVO:
     *      对外传输对象
     */
    private List<ChatMessageVO> convert(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> {
                    ChatMessageVO vo = new ChatMessageVO();
                    vo.setRole(message.getRole());
                    vo.setContent(message.getContent());
                    vo.setCreateTime(message.getCreateTime());
                    return vo;
                })
                .toList();
    }


    @Override
    public List<ChatMessage> getFallbackHistory(Long userId, Long messageId) {
        List<ChatMessage> messages = messageMapper.selectHistoryUntilMessage(userId, messageId);
        log.info(
                "加载MySQL兜底历史消息，userId:{} messageId:{} count:{}",
                userId,
                messageId,
                messages.size()
        );
        return messages;
    }

}
