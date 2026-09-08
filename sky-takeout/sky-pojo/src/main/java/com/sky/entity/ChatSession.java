package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSession {

    private Long id;

    private Long userId;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String summary;
}