package com.sky.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {


    /**
     * 消息角色
     * USER / ASSISTANT
     */
    private String role;


    /**
     * 消息内容
     */
    private String content;


    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}