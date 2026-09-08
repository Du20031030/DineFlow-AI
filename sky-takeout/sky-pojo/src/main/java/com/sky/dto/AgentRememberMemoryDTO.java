package com.sky.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent长期记忆写入请求
 */
@Data
public class AgentRememberMemoryDTO {

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
     * 记忆值
     *
     * 例如：
     * "微辣"
     *
     * 也可能是Map、Number等
     */
    private Object value;

    /**
     * 操作类型
     * SET / ADD / REMOVE
     */
    private String operation;

    /**
     * 来源引用
     * 当前可以直接传threadId
     */
    private String sourceRef;

    /**
     * 形成该记忆的简短依据
     */
    private String evidenceSummary;

    /**
     * 失效时间
     * null表示长期有效
     */
    private LocalDateTime expiresAt;
}