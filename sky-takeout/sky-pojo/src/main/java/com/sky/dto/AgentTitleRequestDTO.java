package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Java -> Python
 * 会话标题生成请求
 */
@Data
@Builder
public class AgentTitleRequestDTO {

    /**
     * 第一轮用户消息
     */
    @JsonProperty("user_message")
    private String userMessage;

    /**
     * 第一轮AI完整回复
     */
    @JsonProperty("assistant_message")
    private String assistantMessage;
}