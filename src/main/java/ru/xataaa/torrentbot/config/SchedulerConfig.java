package ru.xataaa.torrentbot.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Value("${search.inline-worker-pool-size:4}")
    private int inlineWorkerPoolSize;

    @Value("${telegram.worker-pool-size:4}")
    private int telegramWorkerPoolSize;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(4);
        taskScheduler.setThreadNamePrefix("torrentbot-scheduler-");
        taskScheduler.initialize();
        taskRegistrar.setTaskScheduler(taskScheduler);
    }

    @Bean(name = "inlineQueryExecutor")
    public Executor inlineQueryExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(Math.max(1, inlineWorkerPoolSize));
        taskExecutor.setMaxPoolSize(Math.max(1, inlineWorkerPoolSize * 2));
        taskExecutor.setQueueCapacity(100);
        taskExecutor.setThreadNamePrefix("inline-query-");
        taskExecutor.initialize();
        return taskExecutor;
    }

    @Bean(name = "telegramWorkExecutor")
    public Executor telegramWorkExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(Math.max(1, telegramWorkerPoolSize));
        taskExecutor.setMaxPoolSize(Math.max(1, telegramWorkerPoolSize * 2));
        taskExecutor.setQueueCapacity(200);
        taskExecutor.setThreadNamePrefix("telegram-work-");
        taskExecutor.initialize();
        return taskExecutor;
    }
}
