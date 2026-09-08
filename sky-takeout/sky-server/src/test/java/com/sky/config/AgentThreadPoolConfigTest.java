package com.sky.config;

import com.sky.properties.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentThreadPoolConfigTest {

    @Test
    void agentExecutorUsesConfiguredPoolSettings() {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:8000");
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(120));
        properties.getExecutor().setCorePoolSize(2);
        properties.getExecutor().setMaxPoolSize(5);
        properties.getExecutor().setQueueCapacity(7);
        properties.getExecutor().setKeepAliveSeconds(11);
        properties.getExecutor().setAwaitTerminationSeconds(13);

        ThreadPoolTaskExecutor executor =
                (ThreadPoolTaskExecutor) new AgentThreadPoolConfig(properties).agentExecutor();

        assertEquals(2, executor.getCorePoolSize());
        assertEquals(5, executor.getMaxPoolSize());
        assertEquals(7, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
        assertEquals(11, executor.getKeepAliveSeconds());
        assertEquals("agent-executor-", executor.getThreadNamePrefix());

        executor.shutdown();
    }
}
