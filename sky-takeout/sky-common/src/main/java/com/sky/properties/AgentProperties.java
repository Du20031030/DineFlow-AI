package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "sky.agent")
@Data
public class AgentProperties {

    private String baseUrl;

    private Duration connectTimeout;

    private Duration readTimeout;

    private ExecutorProperties executor = new ExecutorProperties();

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            this.baseUrl = null;
            return;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
    }

    @PostConstruct
    public void validate() {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("sky.agent.base-url must not be blank");
        }
        validateTimeout("sky.agent.connect-timeout", connectTimeout);
        validateTimeout("sky.agent.read-timeout", readTimeout);

        if (executor.corePoolSize <= 0) {
            throw new IllegalArgumentException("sky.agent.executor.core-pool-size must be greater than 0");
        }
        if (executor.maxPoolSize < executor.corePoolSize) {
            throw new IllegalArgumentException("sky.agent.executor.max-pool-size must be greater than or equal to core-pool-size");
        }
        if (executor.queueCapacity < 0) {
            throw new IllegalArgumentException("sky.agent.executor.queue-capacity must be greater than or equal to 0");
        }
        if (executor.keepAliveSeconds <= 0) {
            throw new IllegalArgumentException("sky.agent.executor.keep-alive-seconds must be greater than 0");
        }
        if (executor.awaitTerminationSeconds <= 0) {
            throw new IllegalArgumentException("sky.agent.executor.await-termination-seconds must be greater than 0");
        }
    }

    private void validateTimeout(String name, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
        if (timeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must not exceed " + Integer.MAX_VALUE + " milliseconds");
        }
    }

    @Data
    public static class ExecutorProperties {

        private int corePoolSize;

        private int maxPoolSize;

        private int queueCapacity;

        private int keepAliveSeconds;

        private int awaitTerminationSeconds;
    }
}
