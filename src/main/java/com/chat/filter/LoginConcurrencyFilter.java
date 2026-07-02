package com.chat.filter;

import com.chat.api.Result;
import com.chat.exception.ErrorCode;
import com.chat.utils.consts.LoginMetricNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class LoginConcurrencyFilter extends OncePerRequestFilter {

    private final Semaphore semaphore;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public LoginConcurrencyFilter(int limit, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.semaphore = new Semaphore(limit);
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;

        Gauge.builder(LoginMetricNames.LOGIN_PERMIT_IN_USE, semaphore,
                        s -> limit - s.availablePermits())
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!semaphore.tryAcquire()) {
            meterRegistry.counter(LoginMetricNames.LOGIN_PERMIT_REJECTED_TOTAL).increment();
            writeTooManyRequestsResponse(response);
            return;
        }

        Timer.Sample holdSample = Timer.start(meterRegistry);
        try {
            filterChain.doFilter(request, response);
        } finally {
            holdSample.stop(meterRegistry.timer(LoginMetricNames.LOGIN_PERMIT_HOLD_DURATION));
            semaphore.release();
        }
    }

    private void writeTooManyRequestsResponse(HttpServletResponse
                                                      response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "1");

        Result<?> body = Result.builder()
                .status(ErrorCode.SERVER_BUSY.getStatus())
                .message(ErrorCode.SERVER_BUSY.getErrorMessage())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
