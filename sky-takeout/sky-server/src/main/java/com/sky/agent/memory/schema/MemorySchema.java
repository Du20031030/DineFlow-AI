package com.sky.agent.memory.schema;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
@AllArgsConstructor
public class MemorySchema {

    /**
     * 完整Schema标识
     * 例如：food.taste.spicy_level
     */
    private String schemaKey;

    /**
     * 记忆类型
     * preference / fact / goal / constraint
     */
    private String memoryType;

    /**
     * Value的数据类型
     */
    private MemoryValueType valueType;

    /**
     * 单值还是多值
     */
    private MemoryCardinality cardinality;

    /**
     * 允许执行的操作
     */
    private Set<MemoryOperation> allowedOperations;

    /**
     * ENUM类型时允许的值
     * 非ENUM类型可以为null
     */
    private Set<String> allowedValues;

    /**
     * 默认重要程度
     */
    private BigDecimal defaultImportance;
}