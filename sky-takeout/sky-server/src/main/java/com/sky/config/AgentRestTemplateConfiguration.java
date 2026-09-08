package com.sky.config;

import com.sky.properties.AgentProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AgentRestTemplateConfiguration {

    @Bean("agentRestTemplate")
    public RestTemplate agentRestTemplate(RestTemplateBuilder restTemplateBuilder, AgentProperties agentProperties) {
        return restTemplateBuilder
                .setConnectTimeout(agentProperties.getConnectTimeout())
                .setReadTimeout(agentProperties.getReadTimeout())
                .build();
    }
}
