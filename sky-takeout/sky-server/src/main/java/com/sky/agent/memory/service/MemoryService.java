package com.sky.agent.memory.service;

import com.sky.agent.memory.model.MemoryCandidate;
import com.sky.agent.memory.model.MemoryWriteResult;
import com.sky.entity.AgentMemoryItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent长期记忆服务
 */
public interface MemoryService {

    /**
     * 保存或更新一条长期记忆
     *
     * @param userId          当前用户ID
     * @param candidate       候选记忆
     * @param sourceRef       来源引用，例如threadId
     * @param evidenceSummary 记忆形成依据
     * @param expiresAt       失效时间，null表示长期有效
     */
    MemoryWriteResult remember(
            Long userId,
            MemoryCandidate candidate,
            String sourceRef,
            String evidenceSummary,
            LocalDateTime expiresAt
    );

    /**
     * 查询当前用户在指定namespace下的所有有效长期记忆
     */
    List<AgentMemoryItem> getMemories(
            Long userId,
            String namespace
    );


}