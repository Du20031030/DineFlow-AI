package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent长期记忆实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryItem {

    /**
     * 主键
     */
    private Long id;

    /**
     * 记忆主体类型
     * 当前固定为 user
     */
    private String subjectType;

    /**
     * 记忆主体ID
     * 当前对应 userId
     */
    private Long subjectId;

    /**
     * 记忆类型
     * preference / fact / goal / constraint
     */
    private String memoryType;

    /**
     * 记忆所属领域
     * 例如：food.taste
     */
    private String namespace;

    /**
     * 记忆键
     * 例如：spicy_level
     */
    private String memoryKey;

    /**
     * 原子记忆唯一标识
     * SINGLE类型固定为 _single
     * MULTI类型由MemoryManager生成
     */
    private String entryKey;

    /**
     * JSON格式的记忆值
     *
     * 例如：
     * "微辣"
     *
     * 或：
     * {"min":20,"max":50,"currency":"CNY"}
     */
    private String memoryValue;

    /**
     * 记忆来源
     * explicit / inferred
     */
    private String sourceType;

    /**
     * 置信度 0~1
     */
    private BigDecimal confidence;

    /**
     * 重要程度 0~1
     */
    private BigDecimal importance;

    /**
     * 来源引用
     * 例如 threadId
     */
    private String sourceRef;

    /**
     * 形成该记忆的简短依据
     */
    private String evidenceSummary;

    /**
     * 失效时间
     * NULL表示长期有效
     */
    private LocalDateTime expiresAt;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}