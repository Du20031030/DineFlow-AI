package com.sky.agent.memory.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.memory.model.MemoryCandidate;
import com.sky.agent.memory.model.MemoryWriteResult;
import com.sky.agent.memory.schema.MemoryOperation;
import com.sky.agent.memory.schema.MemoryScene;
import com.sky.agent.memory.schema.MemorySceneRegistry;
import com.sky.agent.memory.service.MemoryService;
import com.sky.dto.AgentRememberMemoryDTO;
import com.sky.entity.AgentMemoryItem;
import com.sky.result.Result;
import com.sky.vo.AgentMemoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent内部长期记忆接口
 *
 * 当前供Python Agent调用。
 */
@RestController
@RequestMapping("/internal/agent/memory")
public class AgentMemoryController {

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private ObjectMapper objectMapper;


    /**
     * 新增或更新一条长期记忆
     */
    @PostMapping("/{userId}")
    public Result remember(
            @PathVariable Long userId,
            @RequestBody AgentRememberMemoryDTO dto) {

        // 1. HTTP中的字符串操作类型转换成系统枚举
        MemoryOperation operation;

        try {

            operation = MemoryOperation.valueOf(
                    dto.getOperation().toUpperCase()
            );

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "非法的Memory操作类型：" + dto.getOperation()
            );
        }


        // 2. DTO -> MemoryCandidate
        MemoryCandidate candidate =
                new MemoryCandidate(
                        dto.getNamespace(),
                        dto.getMemoryKey(),
                        dto.getValue(),
                        operation
                );


        // 3. 交给MemoryService处理
        MemoryWriteResult result =
                memoryService.remember(
                        userId,
                        candidate,
                        dto.getSourceRef(),
                        dto.getEvidenceSummary(),
                        dto.getExpiresAt()
                );

        return Result.success(result.name());
    }


    /**
     * 查询指定namespace下的所有有效长期记忆
     *
     * 例如：
     *
     * GET
     * /internal/agent/memory/4?namespace=food.taste
     */
    @GetMapping("/{userId}")
    public Result<List<AgentMemoryVO>> getMemories(
            @PathVariable Long userId,
            @RequestParam String namespace) {

        // 1. 从Service获取数据库Memory实体
        List<AgentMemoryItem> memoryItems =
                memoryService.getMemories(
                        userId,
                        namespace
                );


        // 2. Entity -> VO
        List<AgentMemoryVO> memoryVOList =
                memoryItems.stream()
                        .map(this::toMemoryVO)
                        .collect(Collectors.toList());


        // 3. 返回给Python Agent
        return Result.success(memoryVOList);
    }


    /**
     * AgentMemoryItem -> AgentMemoryVO
     */
    private AgentMemoryVO toMemoryVO(
            AgentMemoryItem memoryItem) {

        Object value;

        try {

            /*
             * memoryValue在数据库中是JSON。
             *
             * 例如：
             *
             * "中辣"
             *
             * 这里反序列化以后，
             * value就是普通Java String：
             *
             * 中辣
             */
            value = objectMapper.readValue(
                    memoryItem.getMemoryValue(),
                    Object.class
            );

        } catch (JsonProcessingException e) {

            throw new IllegalArgumentException(
                    "Memory Value反序列化失败",
                    e
            );
        }


        return AgentMemoryVO.builder()
                .memoryType(
                        memoryItem.getMemoryType()
                )
                .namespace(
                        memoryItem.getNamespace()
                )
                .memoryKey(
                        memoryItem.getMemoryKey()
                )
                .value(
                        value
                )
                .sourceType(
                        memoryItem.getSourceType()
                )
                .confidence(
                        memoryItem.getConfidence()
                )
                .importance(
                        memoryItem.getImportance()
                )
                .expiresAt(
                        memoryItem.getExpiresAt()
                )
                .build();
    }

    @GetMapping("/{userId}/scene")
    public Result<List<AgentMemoryVO>> getByScene(
            @PathVariable Long userId,
            @RequestParam String scene
    ){

        MemoryScene memoryScene =
                MemoryScene.valueOf(
                        scene.toUpperCase()
                );


        List<String> namespaces =
                MemorySceneRegistry
                        .getNamespaces(
                                memoryScene
                        );


        List<AgentMemoryVO> result =
                new ArrayList<>();


        for(String namespace : namespaces){

            result.addAll(

                    memoryService
                            .getMemories(
                                    userId,
                                    namespace
                            )
                            .stream()
                            .map(this::toMemoryVO)
                            .toList()
            );
        }


        return Result.success(result);
    }

}