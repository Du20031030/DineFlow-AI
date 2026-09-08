package com.sky.service.impl;


import com.sky.client.AgentClient;
import com.sky.context.BaseContext;
import com.sky.dto.AgentChatRequestDTO;
import com.sky.dto.AgentChatResponseDTO;
import com.sky.dto.UserAgentChatDTO;
import com.sky.entity.ChatMessage;
import com.sky.entity.ChatSession;
import com.sky.mapper.ChatMessageMapper;
import com.sky.mapper.ChatSessionMapper;
import com.sky.service.*;
import com.sky.context.ChatMemoryContext;
import com.sky.vo.UserAgentChatVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;


@Service
@Slf4j
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    /**
     * 会话服务
     *
     * 创建session
     */
    private final ChatService chatService;

    /**
     * 记忆服务
     *
     * 加载历史
     * 保存消息
     */
    private final MemoryService memoryService;


    /**
     * Python Agent调用客户端
     */
    private final AgentClient agentClient;

    /**
     * 用于解析 Python SSE 返回的 JSON
     */
    private final ObjectMapper objectMapper;

    private final ChatMessageMapper  chatMessageMapper;

    private final ChatSessionMapper  chatSessionMapper;

    @Resource(name = "agentExecutor")
    private Executor agentExecutor;


//    普通聊天
    @Override
    public UserAgentChatVO chat(UserAgentChatDTO dto) {

        // 当前用户
        Long userId = BaseContext.getCurrentId();

        // 当前会话
        Long sessionId = dto.getSessionId();

        /*
         * 第一次聊天
         *
         * 创建新的session
         */
        if(sessionId == null){
            sessionId = chatService.createSession(userId
            );
        }


//        保存用户消息
        memoryService.saveMessage(sessionId, userId, "USER", dto.getMessage());

        /*
         * threadId
         *
         * 对应LangGraph线程
         */
        String threadId = dto.getThreadId();

        if(threadId == null || threadId.trim().isEmpty()){
            threadId = "session_" + sessionId;
        }

        log.info("用户聊天 userId:{} sessionId:{} message:{}", userId, sessionId, dto.getMessage());


        /*
         * 构造Agent请求
         */
        AgentChatRequestDTO request =
                AgentChatRequestDTO.builder()
                        .userId(userId)
                        .role("USER")
                        .message(dto.getMessage())
                        .threadId(threadId)
                        .build();

        /*
         * 调用Python Agent
         */
        AgentChatResponseDTO response = agentClient.chat(request);
        if(response == null){
            throw new RuntimeException(
                    "Agent服务异常"
            );
        }

//        保存AI回复消息
        memoryService.saveMessage(sessionId, userId, "ASSISTANT", response.getContent());

        chatService.touchSession(sessionId);

        return UserAgentChatVO.builder()
                .sessionId(sessionId)
                .threadId(threadId)
                .content(response.getContent())
                .build();

    }



//   流式输出
    @Override
    public SseEmitter streamChat(UserAgentChatDTO dto) {

            // =====================================================
            // 1. 获取当前登录用户
            // =====================================================

            Long userId = BaseContext.getCurrentId();

            Long sessionId = dto.getSessionId();


            // =====================================================
            // 2. 判断是否为新会话
            // =====================================================

            boolean newSession = sessionId == null;

            if (newSession) {
                sessionId = chatService.createSession(userId);
            }


            // =====================================================
            // 3. USER消息先保存到MySQL
            //
            // 即使后续：
            //
            // - PostgresSaver失败
            // - fallback失败
            // - LLM失败
            // - Python服务失败
            //
            // 用户发送的消息仍然会保留下来。
            // =====================================================

            Long messageId = memoryService.saveMessage(
                    sessionId,
                    userId,
                    "USER",
                    dto.getMessage()
            );

            log.info(
                    "当前USER消息Id:{}",
                    messageId
            );


            // =====================================================
            // 4. sessionId -> LangGraph threadId
            // =====================================================

            String threadId = dto.getThreadId();

            if (
                    threadId == null
                            || threadId.trim().isEmpty()
            ) {

                threadId =
                        "session_" + sessionId;
            }


            // =====================================================
            // 5. 创建SSE连接
            // =====================================================

            SseEmitter emitter =
                    new SseEmitter(
                            30 * 60 * 1000L
                    );


            // lambda中使用
            Long finalSessionId = sessionId;
            String finalThreadId = threadId;
            boolean finalNewSession = newSession;


            // =====================================================
            // 6. 构建Java -> Python请求
            // =====================================================

            AgentChatRequestDTO request =
                    AgentChatRequestDTO.builder()
                            .userId(userId)
                            .role("USER")
                            .message(dto.getMessage())
                            .threadId(finalThreadId)
                            .messageId(messageId)
                            .build();


            // =====================================================
            // 7. 先向前端发送meta信息
            // =====================================================

            try {

                Map<String, Object> meta =
                        new HashMap<>();

                meta.put(
                        "type",
                        "meta"
                );

                meta.put(
                        "sessionId",
                        finalSessionId
                );

                meta.put(
                        "threadId",
                        finalThreadId
                );


                emitter.send(
                        SseEmitter.event()
                                .data(
                                        meta,
                                        MediaType.APPLICATION_JSON
                                )
                );

            } catch (Exception e) {

                log.error(
                        "发送Agent meta事件失败，sessionId:{}",
                        finalSessionId,
                        e
                );

                emitter.completeWithError(e);

                return emitter;
            }


            // =====================================================
            // 8. 异步执行Python Agent
            // =====================================================

            CompletableFuture.runAsync(() -> {


                /*
                 * 用于收集本轮AI完整回答。
                 *
                 * token：
                 *      一边发前端
                 *      一边追加到fullContent
                 *
                 * done：
                 *      才允许保存ASSISTANT
                 *
                 * error：
                 *      绝不保存ASSISTANT
                 */
                StringBuilder fullContent =
                        new StringBuilder();


                /*
                 * 防止极端情况下done/error重复处理。
                 */
                AtomicBoolean finished =
                        new AtomicBoolean(false);


                try {

                    agentClient.streamChat(
                            request,

                            eventData -> {

                                // 已经结束后忽略后续事件
                                if (finished.get()) {
                                    return;
                                }


                                try {

                                    // =================================
                                    // Python SSE JSON
                                    // =================================

                                    JsonNode event =
                                            objectMapper.readTree(
                                                    eventData
                                            );


                                    String type =
                                            event.path("type")
                                                    .asText();


                                    // =================================
                                    // token
                                    // =================================

                                    if ("token".equals(type)) {

                                        String content =
                                                event.path("content")
                                                        .asText("");


                                        if (!content.isEmpty()) {

                                            // 保存到本轮临时缓冲区
                                            fullContent.append(
                                                    content
                                            );


                                            // 实时转发给前端
                                            emitter.send(
                                                    SseEmitter.event()
                                                            .data(
                                                                    event,
                                                                    MediaType.APPLICATION_JSON
                                                            )
                                            );
                                        }


                                        return;
                                    }


                                    // =================================
                                    // done
                                    // =================================

                                    if ("done".equals(type)) {

                                        /*
                                         * 保证done只处理一次。
                                         */
                                        if (
                                                !finished.compareAndSet(
                                                        false,
                                                        true
                                                )
                                        ) {

                                            return;
                                        }


                                        String assistantContent =
                                                fullContent
                                                        .toString()
                                                        .trim();


                                        // =============================
                                        // 只有done才保存ASSISTANT
                                        // =============================

                                        if (
                                                !assistantContent.trim().isEmpty()
                                        ) {

                                            memoryService.saveMessage(
                                                    finalSessionId,
                                                    userId,
                                                    "ASSISTANT",
                                                    assistantContent
                                            );
                                        }


                                        // =============================
                                        // 更新会话活跃时间
                                        // =============================

                                        chatService.touchSession(
                                                finalSessionId
                                        );


                                        // =============================
                                        // 新会话第一轮成功后生成标题
                                        // =============================

                                        if (
                                                finalNewSession
                                                && !assistantContent.trim().isEmpty()
                                        ) {

                                            try {

                                                String title =
                                                        agentClient.generateTitle(
                                                                dto.getMessage(),
                                                                assistantContent
                                                        );


                                                chatService.updateTitle(
                                                        finalSessionId,
                                                        title
                                                );


                                                Map<String, Object> titleEvent =
                                                        new HashMap<>();


                                                titleEvent.put(
                                                        "type",
                                                        "title"
                                                );

                                                titleEvent.put(
                                                        "sessionId",
                                                        finalSessionId
                                                );

                                                titleEvent.put(
                                                        "title",
                                                        title
                                                );


                                                emitter.send(
                                                        SseEmitter.event()
                                                                .data(
                                                                        titleEvent,
                                                                        MediaType.APPLICATION_JSON
                                                                )
                                                );


                                                log.info(
                                                        "新会话标题生成成功，sessionId:{} title:{}",
                                                        finalSessionId,
                                                        title
                                                );

                                            } catch (Exception e) {

                                                /*
                                                 * 标题失败不能影响聊天结果。
                                                 */
                                                log.warn(
                                                        "生成会话标题失败，sessionId:{}",
                                                        finalSessionId,
                                                        e
                                                );
                                            }
                                        }


                                        // =============================
                                        // done通知前端
                                        // =============================

                                        emitter.send(
                                                SseEmitter.event()
                                                        .data(
                                                                event,
                                                                MediaType.APPLICATION_JSON
                                                        )
                                        );


                                        emitter.complete();


                                        log.info(
                                                "Agent本轮正常完成，threadId:{}",
                                                finalThreadId
                                        );


                                        return;
                                    }


                                    // =================================
                                    // error
                                    // =================================

                                    if ("error".equals(type)) {

                                        /*
                                         * error只处理一次。
                                         */
                                        if (
                                                !finished.compareAndSet(
                                                        false,
                                                        true
                                                )
                                        ) {

                                            return;
                                        }


                                        /*
                                         * 非常重要：
                                         *
                                         * 到这里绝对不能：
                                         *
                                         * memoryService.saveMessage(
                                         *     ...,
                                         *     "ASSISTANT",
                                         *     fullContent
                                         * );
                                         *
                                         * 因为这一轮Agent没有正常完成。
                                         */


                                        log.warn(
                                                "Agent本轮执行失败，threadId:{} code:{} message:{}",
                                                finalThreadId,
                                                event.path("code")
                                                        .asText("UNKNOWN"),
                                                event.path("message")
                                                        .asText("")
                                        );


                                        /*
                                         * Python传来的友好error
                                         * 原样转发前端。
                                         */
                                        emitter.send(
                                                SseEmitter.event()
                                                        .data(
                                                                event,
                                                                MediaType.APPLICATION_JSON
                                                        )
                                        );


                                        /*
                                         * error属于业务上的正常结束，
                                         * 不是SseEmitter本身发生异常。
                                         */
                                        emitter.complete();


                                        return;
                                    }


                                    // =================================
                                    // 未知事件
                                    // =================================

                                    log.debug(
                                            "收到未知Python Agent事件，threadId:{} data:{}",
                                            finalThreadId,
                                            eventData
                                    );


                                } catch (Exception e) {

                                    // =================================
                                    // Java解析/转发Python事件失败
                                    // =================================

                                    log.error(
                                            "处理Python SSE事件失败，threadId:{}",
                                            finalThreadId,
                                            e
                                    );


                                    if (
                                            finished.compareAndSet(
                                                    false,
                                                    true
                                            )
                                    ) {

                                        try {

                                            Map<String, Object> error =
                                                    new HashMap<>();


                                            error.put(
                                                    "type",
                                                    "error"
                                            );

                                            error.put(
                                                    "code",
                                                    "AGENT_SERVICE_UNAVAILABLE"
                                            );

                                            error.put(
                                                    "message",
                                                    "智能助手暂时不可用，请稍后再试"
                                            );


                                            emitter.send(
                                                    SseEmitter.event()
                                                            .data(
                                                                    error,
                                                                    MediaType.APPLICATION_JSON
                                                            )
                                            );

                                        } catch (Exception sendException) {

                                            log.warn(
                                                    "发送Agent错误事件失败，threadId:{}",
                                                    finalThreadId,
                                                    sendException
                                            );
                                        }


                                        emitter.complete();
                                    }
                                }
                            }
                    );


                } catch (Exception e) {

                    // =================================================
                    // 9. Java调用Python本身出现最终异常
                    //
                    // 例如：
                    //
                    // Python服务挂了
                    // 网络中断
                    // RestTemplate异常
                    //
                    // 同样：
                    //
                    // 不保存ASSISTANT
                    // 不重新调用Graph
                    // 不发送done
                    // =================================================

                    log.error(
                            "调用Python Agent SSE最终失败，threadId:{}",
                            finalThreadId,
                            e
                    );


                    if (
                            finished.compareAndSet(
                                    false,
                                    true
                            )
                    ) {

                        try {

                            Map<String, Object> error =
                                    new HashMap<>();


                            error.put(
                                    "type",
                                    "error"
                            );

                            error.put(
                                    "code",
                                    "AGENT_SERVICE_UNAVAILABLE"
                            );

                            error.put(
                                    "message",
                                    "智能助手暂时不可用，请稍后再试"
                            );


                            emitter.send(
                                    SseEmitter.event()
                                            .data(
                                                    error,
                                                    MediaType.APPLICATION_JSON
                                            )
                            );

                        } catch (Exception sendException) {

                            log.warn(
                                    "发送最终Agent错误事件失败，threadId:{}",
                                    finalThreadId,
                                    sendException
                            );
                        }


                        emitter.complete();
                    }
                }
            },
//                    agent专用线程池
                    agentExecutor

            );


            // =====================================================
            // 10. Controller立即返回SseEmitter
            // =====================================================

            return emitter;
        }



//    删除会话，同时短期记忆也删除
    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        // 当前登录用户
        Long userId = BaseContext.getCurrentId();
        if (sessionId == null) {
            throw new RuntimeException("会话ID不能为空");
        }

        /*
         * 1. 查询会话并校验归属
         *
         * 不能让用户通过修改sessionId
         * 删除其他用户的会话或短期记忆。
         */
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new RuntimeException("会话不存在");
        }

        /*
         * 2. LangGraph threadId与sessionId确定性绑定
         */
        String threadId = "session_" + sessionId;

        /*
         * 3. 先删除PostgreSQL中的Agent短期记忆
         *
         * 如果这里失败，则直接抛异常，
         * 后面的MySQL数据不会继续删除。
         */
        agentClient.deleteThreadMemory(threadId);

        /*
         * 4. 删除该会话的全部聊天消息
         */
        chatMessageMapper.deleteBySessionId(sessionId);

        /*
         * 5. 最后删除chat_session
         */
        int rows = chatSessionMapper.deleteByIdAndUserId(sessionId, userId);

        if (rows == 0) {
            throw new RuntimeException("会话删除失败");
        }

        log.info("用户会话删除成功，userId:{} sessionId:{} threadId:{}", userId, sessionId, threadId
        );
    }

}
