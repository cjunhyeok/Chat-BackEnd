package com.chat.service.utils;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.utils.consts.LoginMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

    @Value("${bcrypt.semaphore.permits:2}")
    private int permits;

    private final MeterRegistry meterRegistry;

    private Semaphore semaphore;

    public BcryptPasswordEncoder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        this.semaphore = new Semaphore(permits, true);
    }

    @Override
    public String encode(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    @Override
    public boolean match(String password, String encodedPassword) {

        boolean acquired;
        Timer.Sample waitSample = Timer.start(meterRegistry);

        try {
            acquired = semaphore.tryAcquire(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            waitSample.stop(meterRegistry.timer(LoginMetricNames.BCRYPT_PERMIT_WAIT_DURATION));
        }

        if (!acquired) {
            meterRegistry.counter(LoginMetricNames.BCRYPT_PERMIT_REJECTED_TOTAL).increment();
            throw new CustomException(ErrorCode.SERVER_BUSY);
        }

        try {
            return BCrypt.checkpw(password, encodedPassword);
        } finally {
            semaphore.release();
        }
    }
}
