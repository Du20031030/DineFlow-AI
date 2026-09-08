package com.sky.agent.memory.schema;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Agent记忆Schema注册中心
 *
 * 负责定义：
 * 1. 哪些长期记忆允许保存
 * 2. 每种记忆的数据类型
 * 3. 是单值还是多值
 * 4. 允许执行哪些操作
 * 5. 默认重要程度
 */
@Component
public class MemorySchemaRegistry {

    /**
     * key:
     * namespace + "." + memoryKey
     *
     * 例如：
     * food.taste.spicy_level
     */
    private final Map<String, MemorySchema> schemas = new HashMap<>();

    public MemorySchemaRegistry() {

        // 辣度长期偏好
        register(new MemorySchema(
                "food.taste.spicy_level",
                "preference",
                MemoryValueType.ENUM,
                MemoryCardinality.SINGLE,
                Set.of(MemoryOperation.SET),
                Set.of("不辣", "微辣", "中辣", "重辣"),
                new BigDecimal("0.700")
        ));

//        不喜欢长期偏好
        register(new MemorySchema(
                "food.ingredient.disliked",
                "preference",
                MemoryValueType.STRING,
                MemoryCardinality.MULTI,
                Set.of(
                        MemoryOperation.ADD,
                        MemoryOperation.REMOVE
                ),
                null,
                new BigDecimal("0.800")
        ));

    }

    /**
     * 注册Schema
     */
    private void register(MemorySchema schema) {
        schemas.put(schema.getSchemaKey(), schema);
    }

    /**
     * 根据完整schemaKey查询
     *
     * 例如：
     * food.taste.spicy_level
     */
    public MemorySchema get(String schemaKey) {
        return schemas.get(schemaKey);
    }

    /**
     * 根据namespace和memoryKey查询
     *
     * 例如：
     * namespace = food.taste
     * memoryKey = spicy_level
     */
    public MemorySchema get(String namespace, String memoryKey) {
        String schemaKey = namespace + "." + memoryKey;
        return schemas.get(schemaKey);
    }

    /**
     * 判断某种Memory Schema是否存在
     */
    public boolean contains(String namespace, String memoryKey) {
        return get(namespace, memoryKey) != null;
    }
}