package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent长期记忆查询结果
 *
 * 返回给Python Agent使用，
 * 不直接暴露数据库AgentMemoryItem实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryVO {

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
     * 实际记忆值
     *
     * 例如：
     * "中辣"
     *
     * 或：
     * {
     *     "min": 20,
     *     "max": 50,
     *     "currency": "CNY"
     * }
     */
    private Object value;

    /**
     * 来源类型
     * explicit / inferred
     */
    private String sourceType;

    /**
     * 置信度
     */
    private BigDecimal confidence;

    /**
     * 重要程度
     */
    private BigDecimal importance;

    /**
     * 失效时间
     * null表示长期有效
     */
    private LocalDateTime expiresAt;
}