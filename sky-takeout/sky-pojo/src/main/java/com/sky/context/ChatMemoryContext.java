package com.sky.context;

import com.sky.vo.ChatMessageVO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatMemoryContext {


    /**
     * 历史摘要
     */
    private String summary;


    /**
     * 最近聊天记录
     */
    private List<ChatMessageVO> messages;


}