package com.planshift.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncExecutorConfig {

    /**
     * Backs experiment runs so the REST API can return immediately with a
     * RUNNING experiment instead of blocking the HTTP request for the whole
     * run (which can take well over a minute at full dataset scale). Virtual
     * threads (Java 21) are a natural fit here: this workload is almost
     * entirely waiting on JDBC/Postgres, not burning CPU.
     */
    @Bean
    public ExecutorService experimentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
