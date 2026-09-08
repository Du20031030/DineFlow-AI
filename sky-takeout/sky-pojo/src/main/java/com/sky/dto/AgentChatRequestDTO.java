package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sky.vo.ChatMessageVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Java后端发送给Python Agent服务的请求数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequestDTO {


    /**
     * 用户id
     */
    @JsonProperty("user_id")
    private Long userId;


    /**
     * 用户角色
     */
    private String role;


    /**
     * 当前消息
     */
    private String message;


    /**
     * LangGraph线程id
     */
    @JsonProperty("thread_id")
    private String threadId;


    /**
     * 当前用户消息在 MySQL chat_message 中的主键。
     * PostgresSaver 异常时用于准确恢复到当前消息，
     * 避免重复追加本轮 HumanMessage。
     */
    @JsonProperty("message_id")
    private Long messageId;

}