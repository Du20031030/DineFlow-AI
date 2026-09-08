package com.sky.dto;

import lombok.Data;

/**
 * Python -> Java
 * 会话标题生成结果
 */
@Data
public class AgentTitleResponseDTO {

    /**
     * 自动生成的会话标题
     */
    private String title;
}