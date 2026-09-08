package com.sky.controller.agent;

import com.sky.entity.ChatMessage;
import com.sky.result.Result;
import com.sky.service.MemoryService;
import com.sky.vo.AgentHistoryMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/agent/chat")
@RequiredArgsConstructor
//agent聊天历史，兜底能力
public class AgentChatController {

    private final MemoryService memoryService;

    /**
     * PostgresSaver异常时，
     * 提供MySQL聊天历史作为Agent上下文兜底。
     */
    @GetMapping("/history")
    public Result<List<AgentHistoryMessageVO>> getFallbackHistory(@RequestParam Long userId, @RequestParam Long messageId) {
        List<ChatMessage> messages = memoryService.getFallbackHistory(userId, messageId);

        List<AgentHistoryMessageVO> result =
                messages.stream()
                        .map(message ->
                                AgentHistoryMessageVO.builder()
                                        .id(message.getId())
                                        .role(message.getRole())
                                        .content(message.getContent())
                                        .build()
                        )
                        .toList();

        return Result.success(result);
    }

}