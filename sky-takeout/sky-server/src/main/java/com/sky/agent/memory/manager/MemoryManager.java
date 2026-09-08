package com.sky.agent.memory.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.memory.model.MemoryCandidate;
import com.sky.agent.memory.schema.MemoryCardinality;
import com.sky.agent.memory.schema.MemorySchema;
import com.sky.agent.memory.schema.MemorySchemaRegistry;
import com.sky.agent.memory.schema.MemoryValueType;
import com.sky.entity.AgentMemoryItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent记忆管理器
 *
 * 主要负责：
 * 1. 校验MemoryCandidate是否合法
 * 2. 根据MemorySchema补充系统字段
 * 3. 将MemoryCandidate转换成AgentMemoryItem
 *
 * 后续还会负责：
 * 1. Memory写入
 * 2. Memory更新
 * 3. Memory删除
 * 4. Memory查询
 * 5. 冲突处理
 */
@Component
public class MemoryManager {

    /**
     * Memory规则注册中心
     */
    @Autowired
    private MemorySchemaRegistry memorySchemaRegistry;

    /**
     * JSON序列化工具
     */
    @Autowired
    private ObjectMapper objectMapper;


    /**
     * 校验候选记忆
     *
     * @param candidate LLM生成的候选记忆
     * @return 对应的MemorySchema
     */
    public MemorySchema validate(MemoryCandidate candidate) {

        // 1. candidate不能为空
        if (candidate == null) {
            throw new IllegalArgumentException("MemoryCandidate不能为空");
        }

        // 2. namespace不能为空
        if (candidate.getNamespace() == null
                || candidate.getNamespace().trim().isEmpty()) {

            throw new IllegalArgumentException("namespace不能为空");
        }

        // 3. memoryKey不能为空
        if (candidate.getMemoryKey() == null
                || candidate.getMemoryKey().trim().isEmpty()) {

            throw new IllegalArgumentException("memoryKey不能为空");
        }


        // 4. 根据namespace + memoryKey查询Schema
        MemorySchema schema = memorySchemaRegistry.get(
                candidate.getNamespace(),
                candidate.getMemoryKey()
        );


        // 5. Schema不存在，说明这种Memory不允许保存
        if (schema == null) {

            throw new IllegalArgumentException(
                    "不支持的记忆Schema："
                            + candidate.getNamespace()
                            + "."
                            + candidate.getMemoryKey()
            );
        }


        // 6. 校验操作类型
        if (candidate.getOperation() == null) {
            throw new IllegalArgumentException("Memory操作类型不能为空");
        }

        if (!schema.getAllowedOperations()
                .contains(candidate.getOperation())) {

            throw new IllegalArgumentException(
                    "该记忆不支持操作："
                            + candidate.getOperation()
            );
        }


        // 7. 校验Memory Value
        validateValue(
                schema,
                candidate.getValue()
        );


        // 校验通过，返回对应Schema
        return schema;
    }


    /**
     * 根据Schema校验Memory Value是否合法
     *
     * @param schema Memory规则
     * @param value  候选Memory值
     */
    private void validateValue(
            MemorySchema schema,
            Object value) {

        // value不能为空
        if (value == null) {
            throw new IllegalArgumentException(
                    "memory value不能为空"
            );
        }


        MemoryValueType valueType =
                schema.getValueType();


        switch (valueType) {

            /**
             * 普通字符串
             */
            case STRING:

                if (!(value instanceof String)) {

                    throw new IllegalArgumentException(
                            "memory value必须是字符串"
                    );
                }

                break;


            /**
             * 枚举类型
             */
            case ENUM:

                if (!(value instanceof String)) {

                    throw new IllegalArgumentException(
                            "ENUM类型的memory value必须是字符串"
                    );
                }


                if (schema.getAllowedValues() == null
                        || !schema.getAllowedValues().contains(value)) {

                    throw new IllegalArgumentException(
                            "非法的memory value：" + value
                    );
                }

                break;


            /**
             * 数字类型
             */
            case NUMBER:

                if (!(value instanceof Number)) {

                    throw new IllegalArgumentException(
                            "memory value必须是数字"
                    );
                }

                break;


            /**
             * 布尔类型
             */
            case BOOLEAN:

                if (!(value instanceof Boolean)) {

                    throw new IllegalArgumentException(
                            "memory value必须是布尔值"
                    );
                }

                break;


            /**
             * JSON对象类型
             *
             * LLM经过JSON反序列化以后，
             * 一般会表现为Map
             */
            case OBJECT:

                if (!(value instanceof Map)) {

                    throw new IllegalArgumentException(
                            "memory value必须是对象"
                    );
                }

                break;


            default:

                throw new IllegalArgumentException(
                        "不支持的MemoryValueType："
                                + valueType
                );
        }
    }


    /**
     * 将MemoryCandidate转换成标准的AgentMemoryItem
     *
     * V1当前只处理：
     * 用户明确表达的长期记忆
     *
     * 因此：
     * subjectType = user
     * sourceType = explicit
     * confidence = 1.000
     *
     * @param userId          当前用户ID
     * @param candidate       LLM产生的候选记忆
     * @param sourceRef       Memory来源，例如threadId
     * @param evidenceSummary Memory形成依据
     * @param expiresAt       失效时间，null表示长期有效
     */
    public AgentMemoryItem buildMemoryItem(
            Long userId,
            MemoryCandidate candidate,
            String sourceRef,
            String evidenceSummary,
            LocalDateTime expiresAt) {


        // 1. userId必须存在
        if (userId == null) {

            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }


        // 2. 校验Candidate并获得Schema
        MemorySchema schema =
                validate(candidate);


        // 3. 根据Schema生成entryKey
        String entryKey =
                generateEntryKey(
                        schema,
                        candidate.getValue()
                );


        // 4. 将Value转换成标准JSON字符串
        String memoryValue =
                serializeValue(
                        candidate.getValue()
                );


        // 5. 构造最终Memory实体
        return AgentMemoryItem.builder()

                // Memory属于谁
                .subjectType("user")
                .subjectId(userId)

                // Memory类型
                // 由Schema决定，而不是LLM决定
                .memoryType(
                        schema.getMemoryType()
                )

                // Memory具体内容
                .namespace(
                        candidate.getNamespace()
                )
                .memoryKey(
                        candidate.getMemoryKey()
                )
                .entryKey(
                        entryKey
                )
                .memoryValue(
                        memoryValue
                )

                // V1当前只处理用户明确表达的Memory
                .sourceType("explicit")
                .confidence(
                        new BigDecimal("1.000")
                )

                // importance由Schema决定
                .importance(
                        schema.getDefaultImportance()
                )

                // Memory来源
                .sourceRef(
                        sourceRef
                )
                .evidenceSummary(
                        evidenceSummary
                )

                // Memory生命周期
                .expiresAt(
                        expiresAt
                )

                // 初始版本
                .version(0)

                .build();
    }


    /**
     * 生成原子记忆唯一标识
     *
     * SINGLE：
     * 固定使用 _single
     *
     * MULTI：
     * 对标准化后的value进行SHA-256，
     * 从而保证相同的原子记忆得到相同的entryKey。
     */
    private String generateEntryKey(MemorySchema schema, Object value) {

        // 1. SINGLE类型固定使用_single
        if (schema.getCardinality()
                == MemoryCardinality.SINGLE) {

            return "_single";
        }

        // 2. MULTI类型根据value生成稳定entryKey
        String normalizedValue =
                normalizeEntryValue(value);

        try {

            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = messageDigest.digest(
                    normalizedValue.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder hexString =
                    new StringBuilder();

            for (byte b : hash) {

                String hex =
                        Integer.toHexString(
                                0xff & b
                        );

                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "无法生成Memory entryKey",
                    e
            );
        }
    }


    /**
     * 对用于生成entryKey的value进行标准化。
     *
     * 当前V1主要支持STRING类型MULTI Memory。
     */
    private String normalizeEntryValue(Object value) {

        if (value == null) {
            throw new IllegalArgumentException(
                    "生成entryKey时value不能为空"
            );
        }

        // String去除首尾空格
        if (value instanceof String) {

            String normalized =
                    ((String) value).trim();

            if (normalized.isEmpty()) {

                throw new IllegalArgumentException(
                        "Memory value不能为空字符串"
                );
            }

            return normalized;
        }

        // 其他类型暂时使用标准JSON字符串
        return serializeValue(value);
    }


    /**
     * 将Java对象序列化成标准JSON字符串
     *
     * 例如：
     *
     * 微辣
     *
     * 转换成：
     *
     * "微辣"
     *
     *
     * Map：
     *
     * {
     *     min:20,
     *     max:50
     * }
     *
     * 转换成：
     *
     * {"min":20,"max":50}
     */
    private String serializeValue(Object value) {

        try {

            return objectMapper.writeValueAsString(
                    value
            );

        } catch (JsonProcessingException e) {

            throw new IllegalArgumentException(
                    "memory value序列化失败",
                    e
            );
        }
    }




}
