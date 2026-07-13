package com.chat.config;

import com.chat.utils.consts.MessageMetricNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("broadcastExecutor")
    public Executor broadcastExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("broadcast-");

        executor.setTaskDecorator(task -> {
            Timer.Sample queueWaitSample = Timer.start(meterRegistry);
            return () -> {
                try {
                    queueWaitSample.stop(meterRegistry.timer(MessageMetricNames.BROADCAST_EXECUTOR_QUEUE_WAIT_DURATION));
                } finally {
                    task.run();
                }
            };
        });

        RejectedExecutionHandler callerRunsPolicy = new ThreadPoolExecutor.CallerRunsPolicy();
        // 호출자 스레드가 직접 브로드캐스트
        executor.setRejectedExecutionHandler((rejectedTask, executorService) -> {
            meterRegistry.counter(MessageMetricNames.BROADCAST_EXECUTOR_CALLER_RUNS_TOTAL).increment();
            callerRunsPolicy.rejectedExecution(rejectedTask, executorService);
        });

        executor.initialize();

        Gauge.builder(MessageMetricNames.BROADCAST_EXECUTOR_QUEUE_SIZE, executor,
                        e -> (double) e.getThreadPoolExecutor().getQueue().size())
                .register(meterRegistry);
        Gauge.builder(MessageMetricNames.BROADCAST_EXECUTOR_ACTIVE_THREADS, executor,
                        e -> (double) e.getActiveCount())
                .register(meterRegistry);

        return executor;
    }
}
