package com.sky.agent.memory.model;

/**
 * Memory写操作结果
 */
public enum MemoryWriteResult {

    /**
     * SET / ADD执行成功
     */
    SAVED,

    /**
     * REMOVE确实删除了一条Memory
     */
    REMOVED,

    /**
     * REMOVE时目标Memory原本不存在
     */
    NOT_FOUND
}