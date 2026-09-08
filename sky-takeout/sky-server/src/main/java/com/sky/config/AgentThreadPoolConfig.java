package com.sky.config;

import com.sky.properties.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * Agent专用线程池配置。
 *
 * 用于处理：
 *
 * Java -> Python Agent
 * SSE长连接调用
 *
 * 避免直接使用：
 *
 * ForkJoinPool.commonPool()
 *
 * 导致Agent长时间任务占用JVM公共线程池。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentThreadPoolConfig {

    private final AgentProperties agentProperties;

    @Bean("agentExecutor")
    public Executor agentExecutor() {

        AgentProperties.ExecutorProperties executorProperties = agentProperties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // ==================================================
        // 1. 核心线程数
        //
        // 平时最多保持8个Agent工作线程。
        // ==================================================
        executor.setCorePoolSize(executorProperties.getCorePoolSize());

        // ==================================================
        // 2. 最大线程数
        //
        // 并发增加以后，
        // Agent线程最多扩展到32个。
        // ==================================================

        executor.setMaxPoolSize(executorProperties.getMaxPoolSize());

        // ==================================================
        // 3. 等待队列
        //
        // 当核心线程全部忙碌时，
        // 新任务先进入队列。
        // ==================================================
        executor.setQueueCapacity(executorProperties.getQueueCapacity());

        // ==================================================
        // 4. 非核心线程空闲时间
        // ==================================================
        executor.setKeepAliveSeconds(executorProperties.getKeepAliveSeconds());

        // ==================================================
        // 5. 线程名称前缀
        //
        // 后面看日志时可以直接看到：
        //
        // agent-executor-1
        // agent-executor-2
        // ==================================================

        executor.setThreadNamePrefix("agent-executor-");

        // ==================================================
        // 6. 拒绝策略
        //
        // 当：
        //
        // 最大线程数已满
        // +
        // 队列也已经满
        //
        // CallerRunsPolicy会让提交任务的线程自己执行。
        //
        // 好处：
        // 不直接丢失Agent请求。
        // ==================================================

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );


        // ==================================================
        // 7. Java服务关闭时等待正在执行的Agent任务
        // ==================================================
        executor.setWaitForTasksToCompleteOnShutdown(
                true
        );

        executor.setAwaitTerminationSeconds(
                executorProperties.getAwaitTerminationSeconds()
        );


        // 初始化线程池
        executor.initialize();

        log.info(
                "Agent专用线程池初始化完成，corePoolSize={}，maxPoolSize={}，queueCapacity={}",
                executorProperties.getCorePoolSize(),
                executorProperties.getMaxPoolSize(),
                executorProperties.getQueueCapacity()
        );

        return executor;
    }
}
