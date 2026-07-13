package com.chat.config;

import com.chat.filter.LoginConcurrencyFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Value("${login.concurrent.limit}")
    private int limit;

    @Bean
    public FilterRegistrationBean<LoginConcurrencyFilter> loginConcurrencyFilterRegistrationBean(
            ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        LoginConcurrencyFilter filter = new LoginConcurrencyFilter(limit, objectMapper, meterRegistry);

        FilterRegistrationBean<LoginConcurrencyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/member/login");
        registration.setOrder(1);
        return registration;
    }
}
