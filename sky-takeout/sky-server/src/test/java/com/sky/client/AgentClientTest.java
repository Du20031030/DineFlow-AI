package com.sky.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.AgentChatRequestDTO;
import com.sky.dto.AgentChatResponseDTO;
import com.sky.properties.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AgentClientTest {

    @Test
    void chatUsesConfiguredAgentBaseUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AgentClient agentClient = new AgentClient(restTemplate, new ObjectMapper(), agentProperties("http://agent.local:9000/"));

        server.expect(requestTo("http://agent.local:9000/agent/user/chat"))
                .andRespond(withSuccess("{\"content\":\"ok\"}", MediaType.APPLICATION_JSON));

        AgentChatRequestDTO requestDTO = AgentChatRequestDTO.builder()
                .userId(1L)
                .role("USER")
                .message("hello")
                .threadId("session_1")
                .messageId(10L)
                .build();

        AgentChatResponseDTO response = agentClient.chat(requestDTO);

        assertEquals("ok", response.getContent());
        server.verify();
    }

    private AgentProperties agentProperties(String baseUrl) {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(120));
        properties.getExecutor().setCorePoolSize(8);
        properties.getExecutor().setMaxPoolSize(32);
        properties.getExecutor().setQueueCapacity(100);
        properties.getExecutor().setKeepAliveSeconds(60);
        properties.getExecutor().setAwaitTerminationSeconds(30);
        return properties;
    }
}
