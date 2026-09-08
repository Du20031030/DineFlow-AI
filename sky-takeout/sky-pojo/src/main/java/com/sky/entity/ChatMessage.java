package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {

    private Long id;

    private Long sessionId;

    private Long userId;

    private String role;

    private String content;

    private LocalDateTime createTime;
}
