package com.hify.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean
    @Qualifier("llmExecutor")
    public Executor llmExecutor() {
        return new ThreadPoolExecutor(
                10, 50,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new NamedThreadFactory("llm-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean
    @Qualifier("asyncExecutor")
    public Executor asyncExecutor() {
        return new ThreadPoolExecutor(
                5, 20,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new NamedThreadFactory("async-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
