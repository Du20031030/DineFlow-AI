package com.sky.agent.memory.service.impl;

import com.sky.agent.memory.manager.MemoryManager;
import com.sky.agent.memory.model.MemoryCandidate;
import com.sky.agent.memory.model.MemoryWriteResult;
import com.sky.agent.memory.service.MemoryService;
import com.sky.entity.AgentMemoryItem;
import com.sky.mapper.AgentMemoryItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent长期记忆服务实现
 */
@Service
public class MemoryServiceImpl implements MemoryService {

    @Autowired
    private MemoryManager memoryManager;

    @Autowired
    private AgentMemoryItemMapper agentMemoryItemMapper;


    /**
     * 保存或更新长期记忆
     */
    @Override
    @Transactional
    public MemoryWriteResult remember(
            Long userId,
            MemoryCandidate candidate,
            String sourceRef,
            String evidenceSummary,
            LocalDateTime expiresAt) {

        // 1. 校验并构造标准Memory实体
        AgentMemoryItem memoryItem =
                memoryManager.buildMemoryItem(
                        userId,
                        candidate,
                        sourceRef,
                        evidenceSummary,
                        expiresAt
                );


        // 2. 根据Memory操作类型执行不同处理
        switch (candidate.getOperation()) {

            case SET:

                agentMemoryItemMapper.upsert(memoryItem);

                return MemoryWriteResult.SAVED;


            case ADD:

                agentMemoryItemMapper.upsert(memoryItem);

                return MemoryWriteResult.SAVED;


            case REMOVE:

                int affectedRows =
                        agentMemoryItemMapper.deleteByEntry(
                                memoryItem.getSubjectType(),
                                memoryItem.getSubjectId(),
                                memoryItem.getNamespace(),
                                memoryItem.getMemoryKey(),
                                memoryItem.getEntryKey()
                        );

                if (affectedRows > 0) {
                    return MemoryWriteResult.REMOVED;
                }

                return MemoryWriteResult.NOT_FOUND;


            default:

                throw new IllegalArgumentException(
                        "不支持的Memory操作："
                                + candidate.getOperation()
                );
        }
    }

    /**
     * 查询指定namespace下的所有有效长期记忆
     */
    @Override
    public List<AgentMemoryItem> getMemories(
            Long userId,
            String namespace) {

        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        if (namespace == null || namespace.trim().isEmpty()) {
            throw new IllegalArgumentException("namespace不能为空");
        }

        return agentMemoryItemMapper.findByNamespace(
                "user",
                userId,
                namespace
        );
    }


}
