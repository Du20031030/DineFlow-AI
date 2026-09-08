package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Python Agent服务返回给Java后端的数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponseDTO implements Serializable {

    /**
     * Agent会话id
     */
    @JsonProperty("thread_id")
    private String threadId;

    /**
     * Agent返回的自然语言回答
     */
    private String content;
}