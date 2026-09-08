package com.sky.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.AgentChatRequestDTO;
import com.sky.dto.AgentChatResponseDTO;
import com.sky.dto.AgentTitleRequestDTO;
import com.sky.dto.AgentTitleResponseDTO;
import com.sky.properties.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 用于调用 Python Agent 服务。
 *
 * Java 后端在这里作为 Python Agent 的 HTTP Client。
 */
@Component
@Slf4j
public class AgentClient {

    /**
     * Python Agent 普通聊天接口
     */
    private static final String USER_AGENT_CHAT_PATH = "/agent/user/chat";

    /**
     * Python Agent SSE 流式聊天接口
     */
    private static final String USER_AGENT_CHAT_STREAM_PATH = "/agent/user/chat/stream";

    /**
     * HTTP 客户端
     */
    private final RestTemplate restTemplate;

    /**
     * 用于将 Java DTO 序列化为 JSON
     */
    private final ObjectMapper objectMapper;

    private final AgentProperties agentProperties;

    /**
     * Python Agent 会话标题生成接口
     */
    private static final String USER_AGENT_TITLE_PATH = "/agent/user/title";


    /**
     * Python Agent 删除短期记忆接口
     */
    private static final String USER_AGENT_THREAD_PATH = "/agent/user/thread/";

    public AgentClient(@Qualifier("agentRestTemplate") RestTemplate restTemplate,
                       ObjectMapper objectMapper,
                       AgentProperties agentProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
    }


    /**
     * 普通非流式聊天。
     */
    public AgentChatResponseDTO chat(AgentChatRequestDTO requestDTO) {

        log.info(
                "调用Python Agent服务，message：{}，threadId：{}",
                requestDTO.getMessage(),
                requestDTO.getThreadId()
        );

        AgentChatResponseDTO response = restTemplate.postForObject(
                buildUrl(USER_AGENT_CHAT_PATH),
                requestDTO,
                AgentChatResponseDTO.class
        );

        log.info("Python Agent服务返回：{}", response);

        return response;
    }


    /**
     * 调用 Python Agent SSE 流式接口。
     *
     * Python返回：
     *
     * data: {"type":"token","content":"清炒"}
     *
     * data: {"type":"token","content":"西兰花"}
     *
     * data: {"type":"done"}
     *
     * 这里每收到一个 SSE data 事件，
     * 就立即通过 onEvent 回调交给上层 Service。
     *
     * @param requestDTO 发送给 Python 的请求
     * @param onEvent    每收到一个 SSE 事件时执行的回调
     */

    public void streamChat(AgentChatRequestDTO requestDTO, Consumer<String> onEvent) {

        log.info(
                "开始调用Python Agent SSE，message：{}，threadId：{}",
                requestDTO.getMessage(),
                requestDTO.getThreadId()
        );

        try {

            restTemplate.execute(
                    buildUrl(USER_AGENT_CHAT_STREAM_PATH),
                    HttpMethod.POST,

                    // ==========================================
                    // 构造发送给Python的请求
                    // ==========================================
                    request -> {

                        request.getHeaders().setContentType(
                                MediaType.APPLICATION_JSON
                        );

                        request.getHeaders().setAccept(
                                java.util.List.of(
                                        MediaType.TEXT_EVENT_STREAM
                                )
                        );

                        /*
                         * AgentChatRequestDTO -> JSON
                         *
                         * 当前请求中包含：
                         *
                         * user_id
                         * role
                         * message
                         * thread_id
                         * message_id
                         */
                        objectMapper.writeValue(
                                request.getBody(),
                                requestDTO
                        );
                    },


                    // ==========================================
                    // 持续读取Python返回的SSE
                    // ==========================================
                    response -> {

                        try (
                                BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        response.getBody(),
                                                        StandardCharsets.UTF_8
                                                )
                                        )
                        ) {

                            String line;


                            while (
                                    (line = reader.readLine()) != null
                            ) {

                                /*
                                 * Python返回格式：
                                 *
                                 * data: {"type":"token","content":"你好"}
                                 *
                                 * data: {"type":"done"}
                                 *
                                 * 或：
                                 *
                                 * data: {
                                 *   "type":"error",
                                 *   "code":"AGENT_SERVICE_UNAVAILABLE",
                                 *   "message":"智能助手暂时不可用，请稍后再试"
                                 * }
                                 *
                                 *
                                 * SSE事件之间存在空行。
                                 *
                                 * 这里只关心：
                                 *
                                 * data:
                                 *
                                 * 开头的行。
                                 */
                                if (!line.startsWith("data:")) {
                                    continue;
                                }


                                String data = line
                                        .substring(5)
                                        .trim();


                                if (data.isEmpty()) {
                                    continue;
                                }


                                log.debug(
                                        "收到Python SSE事件：{}",
                                        data
                                );


                                /*
                                 * 非常重要：
                                 *
                                 * 这一层不判断：
                                 *
                                 * token
                                 * done
                                 * error
                                 *
                                 * 而是全部原样交给上层。
                                 *
                                 * 真正的：
                                 *
                                 * - 拼接assistant文本
                                 * - done时保存ASSISTANT
                                 * - error时禁止保存ASSISTANT
                                 *
                                 * 应该由上层Service负责。
                                 */
                                onEvent.accept(
                                        data
                                );
                            }
                        }


                        return null;
                    }
            );


            log.info(
                    "Python Agent SSE正常结束，threadId：{}",
                    requestDTO.getThreadId()
            );

        } catch (RestClientException e) {

            // ==============================================
            // Python服务本身不可访问
            //
            // 例如：
            //
            // - Python没启动
            // - 8000端口不可用
            // - 连接超时
            // - 网络异常
            //
            // 此时Python根本没有机会返回SSE error，
            // 所以由Java客户端补最后一道错误事件。
            // ==============================================

            log.error(
                    "调用Python Agent失败，threadId={}，原因：{}",
                    requestDTO.getThreadId(),
                    e.getMessage(),
                    e
            );


            try {

                String errorEvent =
                        objectMapper.writeValueAsString(
                                java.util.Map.of(
                                        "type",
                                        "error",

                                        "code",
                                        "AGENT_SERVICE_UNAVAILABLE",

                                        "message",
                                        "智能助手暂时不可用，请稍后再试"
                                )
                        );


                /*
                 * 注意：
                 *
                 * 这里只发送error。
                 *
                 * 绝对不要继续发送：
                 *
                 * {"type":"done"}
                 *
                 * 否则上层会误认为本轮Agent正常完成。
                 */
                onEvent.accept(
                        errorEvent
                );

            } catch (Exception jsonException) {

                log.error(
                        "构造Agent错误事件失败，threadId={}",
                        requestDTO.getThreadId(),
                        jsonException
                );
            }
        }
    }


    /**
     * 根据新会话第一轮聊天内容生成会话标题。
     *
     * 该接口只是一次普通LLM调用：
     * 不使用LangGraph
     * 不使用threadId
     * 不写入checkpoint
     *
     * @param userMessage      用户第一轮消息
     * @param assistantMessage AI第一轮完整回复
     * @return 生成的会话标题
     */
    public String generateTitle(String userMessage, String assistantMessage) {
        AgentTitleRequestDTO request =
                AgentTitleRequestDTO.builder()
                        .userMessage(userMessage)
                        .assistantMessage(assistantMessage)
                        .build();
        log.info("调用Python生成会话标题，userMessage：{}", userMessage);

        AgentTitleResponseDTO response =
                restTemplate.postForObject(
                        buildUrl(USER_AGENT_TITLE_PATH),
                        request,
                        AgentTitleResponseDTO.class
                );

        if (response == null || response.getTitle() == null || response.getTitle().trim().isEmpty()) {
            log.warn("Python会话标题生成失败，使用默认标题");
            return "新聊天";
        }

        log.info("Python生成会话标题：{}", response.getTitle());
        return response.getTitle();
    }


    /**
     * 删除指定 LangGraph thread 的短期记忆。
     *
     * @param threadId LangGraph线程ID，例如 session_12
     */
    public void deleteThreadMemory(String threadId) {
        if (threadId == null || threadId.trim().isEmpty()) {
            throw new RuntimeException("threadId不能为空");
        }

        log.info("删除Python Agent短期记忆，threadId：{}", threadId);
        try {
            restTemplate.delete(buildUrl(USER_AGENT_THREAD_PATH + threadId));
            log.info("Python Agent短期记忆删除完成，threadId：{}", threadId);

        } catch (Exception e) {
            log.error("删除Python Agent短期记忆失败，threadId：{}", threadId, e);

            /*
             * 不要吞掉异常。
             *
             * 如果短期记忆删失败，
             * Java后面也不应该继续删除MySQL会话，
             * 否则会出现：
             *
             * MySQL已经没有会话
             * PostgreSQL却还残留checkpoint
             */
            throw new RuntimeException("Agent短期记忆删除失败");
        }
    }

    private String buildUrl(String path) {
        return agentProperties.getBaseUrl() + path;
    }

}
