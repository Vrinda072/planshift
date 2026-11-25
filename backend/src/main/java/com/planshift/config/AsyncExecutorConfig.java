package com.planshift.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AsyncExecutorConfig {

    /**
     * Backs experiment runs so the REST API can return immediately. Deliberately a single worker: two experiments
     * running at once would change indexes underneath each other's measurements, and would compete for the same
     * database cache and CPU even on different tables. Waiting jobs sit in a bounded queue; when it is full new
     * submissions are rejected rather than piling up. This only serialises one application instance -- running
     * several against one database would need a database-level lock as well.
     */
    @Bean
    public ExecutorService experimentExecutor(@Value("${planshift.experiments.queue-capacity:10}") int queueCapacity) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("experiment-", 0).factory());
    }
}
