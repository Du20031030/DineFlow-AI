package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.ChatSessionTitleDTO;
import com.sky.dto.UserAgentChatDTO;
import com.sky.result.Result;
import com.sky.service.AgentChatService;
import com.sky.service.ChatService;
import com.sky.vo.ChatMessageVO;
import com.sky.vo.ChatSessionVO;
import com.sky.vo.UserAgentChatVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;



/**
 * 用户端Agent相关接口
 */
@RestController
@RequestMapping("/user/agent")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "用户端-Agent相关接口")
public class AgentController {

    private final AgentChatService agentChatService;

    private final ChatService chatService;

    /**
     * 用户聊天接口
     */
    @PostMapping("/chat")
    @ApiOperation("普通聊天接口")
    public Result<UserAgentChatVO> chat(@RequestBody UserAgentChatDTO dto){
        return Result.success(agentChatService.chat(dto));
    }


    /**
     * 用户 Agent SSE 流式聊天接口。
     *
     * 前端发送一次 POST 请求后，
     * Java 会保持 HTTP 连接，
     * 持续把 Python Agent 返回的 token 推送给前端。
     */

    @ApiOperation("流式聊天接口")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody UserAgentChatDTO dto) {
        return agentChatService.streamChat(dto);
    }


    /**
     * 查询聊天历史
     *
     * 用户打开聊天窗口时调用
     */
    @ApiOperation("查询历史聊天消息")
    @GetMapping("/history/{sessionId}")
    public Result<List<ChatMessageVO>> history(@PathVariable Long sessionId){
        //当前登录用户
        Long userId = BaseContext.getCurrentId();
        return Result.success(chatService.history(sessionId, userId));
    }



//    手动更新标题
    @ApiOperation("手动更新会话标题")
    @PatchMapping("/session/{sessionId}/title")
    public Result updateSessionTitle(@PathVariable Long sessionId, @RequestBody ChatSessionTitleDTO dto) {

        Long userId = BaseContext.getCurrentId();
        chatService.updateTitle(sessionId, userId, dto.getTitle());
        return Result.success();
    }


    /**
     * 查询当前用户的历史会话列表
     */
    @ApiOperation("查询当前用户的历史会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions() {
        Long userId = BaseContext.getCurrentId();
        List<ChatSessionVO> sessions = chatService.listSessions(userId);
        return Result.success(sessions);
    }


    /**
     * 删除指定聊天会话。
     *
     * 同时删除：
     * 1. MySQL chat_message
     * 2. MySQL chat_session
     * 3. PostgreSQL LangGraph checkpoint
     */
    @ApiOperation("删除会话以及会话里面包含的短期记忆")
    @DeleteMapping("/session/{sessionId}")
    public Result deleteSession(@PathVariable Long sessionId) {
        agentChatService.deleteSession(sessionId);
        return Result.success();
    }

}