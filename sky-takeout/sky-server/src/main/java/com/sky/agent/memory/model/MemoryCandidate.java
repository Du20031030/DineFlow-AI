package com.sky.agent.memory.model;

import com.sky.agent.memory.schema.MemoryOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCandidate {

    /**
     * 记忆所属领域
     * 例如：food.taste
     */
    private String namespace;

    /**
     * 具体记忆键
     * 例如：spicy_level
     */
    private String memoryKey;

    /**
     * 候选记忆值
     * 例如：微辣
     */
    private Object value;

    /**
     * 操作类型
     * SET / ADD / REMOVE
     */
    private MemoryOperation operation;
}