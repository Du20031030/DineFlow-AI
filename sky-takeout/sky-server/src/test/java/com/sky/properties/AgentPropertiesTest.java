package com.sky.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPropertiesTest {

    @Test
    void trimsTrailingSlashFromBaseUrl() {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:8000/");

        assertEquals("http://127.0.0.1:8000", properties.getBaseUrl());
    }

    @Test
    void rejectsInvalidExecutorSizing() {
        AgentProperties properties = validProperties();
        properties.getExecutor().setCorePoolSize(33);
        properties.getExecutor().setMaxPoolSize(32);

        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    private AgentProperties validProperties() {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:8000");
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
