package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentHistoryMessageVO {

    /**
     * MySQL消息主键
     */
    private Long id;

    /**
     * USER / ASSISTANT
     */
    private String role;

    /**
     * 消息正文
     */
    private String content;
}