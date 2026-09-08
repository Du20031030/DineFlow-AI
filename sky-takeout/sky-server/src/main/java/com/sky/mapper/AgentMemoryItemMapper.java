package com.sky.mapper;

import com.sky.entity.AgentMemoryItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentMemoryItemMapper {

    /**
     * 查询一条当前有效的原子记忆
     *
     * SINGLE 类型：
     * entryKey 固定为 _single
     *
     * MULTI 类型以后也可以复用这个方法。
     */
    @Select("""
            select
                id,
                subject_type as subjectType,
                subject_id as subjectId,
                memory_type as memoryType,
                namespace,
                memory_key as memoryKey,
                entry_key as entryKey,
                memory_value as memoryValue,
                source_type as sourceType,
                confidence,
                importance,
                source_ref as sourceRef,
                evidence_summary as evidenceSummary,
                expires_at as expiresAt,
                version,
                created_time as createdTime,
                updated_time as updatedTime
            from agent_memory_item
            where subject_type = #{subjectType}
              and subject_id = #{subjectId}
              and namespace = #{namespace}
              and memory_key = #{memoryKey}
              and entry_key = #{entryKey}
              and (
                    expires_at is null
                    or expires_at > now()
                  )
            limit 1
            """)
    AgentMemoryItem findOne(
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId,
            @Param("namespace") String namespace,
            @Param("memoryKey") String memoryKey,
            @Param("entryKey") String entryKey
    );


    /**
     * 新增或更新长期记忆。
     *
     * 依赖数据库唯一索引：
     *
     * subject_type
     * + subject_id
     * + namespace
     * + memory_key
     * + entry_key
     *
     * 如果记忆不存在：
     * INSERT
     *
     * 如果已经存在：
     * UPDATE
     */
    @Insert("""
            insert into agent_memory_item (
                subject_type,
                subject_id,
                memory_type,
                namespace,
                memory_key,
                entry_key,
                memory_value,
                source_type,
                confidence,
                importance,
                source_ref,
                evidence_summary,
                expires_at,
                version
            )
            values (
                #{subjectType},
                #{subjectId},
                #{memoryType},
                #{namespace},
                #{memoryKey},
                #{entryKey},
                #{memoryValue},
                #{sourceType},
                #{confidence},
                #{importance},
                #{sourceRef},
                #{evidenceSummary},
                #{expiresAt},
                #{version}
            )
            on duplicate key update
                memory_type = values(memory_type),
                memory_value = values(memory_value),
                source_type = values(source_type),
                confidence = values(confidence),
                importance = values(importance),
                source_ref = values(source_ref),
                evidence_summary = values(evidence_summary),
                expires_at = values(expires_at),
                version = version + 1,
                updated_time = current_timestamp
            """)
    void upsert(AgentMemoryItem memoryItem);


    /**
     * 查询某个用户在指定namespace下的所有有效长期记忆
     *
     * 例如：
     * subjectId = 4
     * namespace = food.taste
     */
    @Select("""
        select
            id,
            subject_type as subjectType,
            subject_id as subjectId,
            memory_type as memoryType,
            namespace,
            memory_key as memoryKey,
            entry_key as entryKey,
            memory_value as memoryValue,
            source_type as sourceType,
            confidence,
            importance,
            source_ref as sourceRef,
            evidence_summary as evidenceSummary,
            expires_at as expiresAt,
            version,
            created_time as createdTime,
            updated_time as updatedTime
        from agent_memory_item
        where subject_type = #{subjectType}
          and subject_id = #{subjectId}
          and namespace = #{namespace}
          and (
                expires_at is null
                or expires_at > now()
              )
        order by importance desc, updated_time desc
        """)
    List<AgentMemoryItem> findByNamespace(
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId,
            @Param("namespace") String namespace
    );


    /**
     * 根据原子记忆唯一标识删除一条长期记忆
     *
     * 主要用于MULTI Memory的REMOVE操作。
     *
     * 例如：
     * food.ingredient.disliked = 香菜
     */
    @Delete("""
        delete from agent_memory_item
        where subject_type = #{subjectType}
          and subject_id = #{subjectId}
          and namespace = #{namespace}
          and memory_key = #{memoryKey}
          and entry_key = #{entryKey}
        """)
    int deleteByEntry(
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId,
            @Param("namespace") String namespace,
            @Param("memoryKey") String memoryKey,
            @Param("entryKey") String entryKey
    );


}