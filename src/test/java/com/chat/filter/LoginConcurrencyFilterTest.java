package com.chat.filter;

import com.chat.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LoginConcurrencyFilterTest {

    private LoginConcurrencyFilter loginConcurrencyFilter;

    @BeforeEach
    void setUp() {
        loginConcurrencyFilter = new LoginConcurrencyFilter(1, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("permit이 있으면 로그인 요청을 FilterChain으로 전달한다.")
    void permit이_있으면_로그인_요청을_FilterChain으로_전달한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/member/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // when
        loginConcurrencyFilter.doFilter(request, response, filterChain);

        // then
        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("FilterChain에서 예외가 발생해도 permit을 반환한다.")
    void FilterChain에서_예외가_발생해도_permit을_반환한다() throws Exception {
        // given
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", "/api/member/login");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        FilterChain failingFilterChain = mock(FilterChain.class);
        willThrow(new ServletException("downstream failure"))
                .given(failingFilterChain).doFilter(firstRequest, firstResponse);

        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/api/member/login");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        FilterChain succeedingFilterChain = mock(FilterChain.class);

        // when & then
        assertThatThrownBy(() -> loginConcurrencyFilter.doFilter(firstRequest, firstResponse, failingFilterChain))
                .isInstanceOf(ServletException.class);

        loginConcurrencyFilter.doFilter(secondRequest, secondResponse, succeedingFilterChain);

        verify(succeedingFilterChain, times(1)).doFilter(secondRequest, secondResponse);
        assertThat(secondResponse.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("permit이 고갈되면 429 SERVER_BUSY를 응답하고 FilterChain을 호출하지 않는다.")
    void permit이_고갈되면_429_SERVER_BUSY를_응답하고_FilterChain을_호출하지_않는다() throws Exception {
        // given
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", "/api/member/login");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();

        CountDownLatch firstRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);

        FilterChain blockingFilterChain = (req, res) -> {
            firstRequestEntered.countDown();
            try {
                boolean released = releaseFirstRequest.await(3, TimeUnit.SECONDS);
                assertThat(released).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("releaseFirstRequest 대기 중 인터럽트됨", e);
            }
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> firstRequestFuture = null;

        try {
            // when: 첫 번째 요청이 별도 thread에서 permit을 점유한 채 대기한다.
            firstRequestFuture = executor.submit(() -> {
                try {
                    loginConcurrencyFilter.doFilter(firstRequest, firstResponse, blockingFilterChain);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            boolean entered = firstRequestEntered.await(3, TimeUnit.SECONDS);
            assertThat(entered).isTrue();

            MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/api/member/login");
            MockHttpServletResponse secondResponse = new MockHttpServletResponse();
            FilterChain secondFilterChain = mock(FilterChain.class);

            loginConcurrencyFilter.doFilter(secondRequest, secondResponse, secondFilterChain);

            // then
            assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("1");
            assertThat(secondResponse.getContentType()).isEqualTo("application/json;charset=UTF-8");

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode body = objectMapper.readTree(secondResponse.getContentAsString());
            assertThat(HttpStatus.valueOf(body.get("status").asText())).isEqualTo(ErrorCode.SERVER_BUSY.getStatus());
            assertThat(body.get("message").asText()).isEqualTo(ErrorCode.SERVER_BUSY.getErrorMessage());

            verifyNoInteractions(secondFilterChain);
        } finally {
            releaseFirstRequest.countDown();
            try {
                if (firstRequestFuture != null) {
                    firstRequestFuture.get(3, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("permit이 고갈되어도 OPTIONS 요청은 FilterChain으로 전달한다.")
    void permit이_고갈되어도_OPTIONS_요청은_FilterChain으로_전달한다() throws Exception {
        // given
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", "/api/member/login");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();

        CountDownLatch firstRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);

        FilterChain blockingFilterChain = (req, res) -> {
            firstRequestEntered.countDown();
            try {
                boolean released = releaseFirstRequest.await(3, TimeUnit.SECONDS);
                assertThat(released).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("releaseFirstRequest 대기 중 인터럽트됨", e);
            }
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> firstRequestFuture = null;

        try {
            // when: 첫 번째 POST 요청이 별도 thread에서 유일한 permit을 점유한 채 대기한다.
            firstRequestFuture = executor.submit(() -> {
                try {
                    loginConcurrencyFilter.doFilter(firstRequest, firstResponse, blockingFilterChain);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            boolean entered = firstRequestEntered.await(3, TimeUnit.SECONDS);
            assertThat(entered).isTrue();

            MockHttpServletRequest optionsRequest = new MockHttpServletRequest("OPTIONS", "/api/member/login");
            MockHttpServletResponse optionsResponse = new MockHttpServletResponse();
            FilterChain optionsFilterChain = mock(FilterChain.class);

            loginConcurrencyFilter.doFilter(optionsRequest, optionsResponse, optionsFilterChain);

            // then
            verify(optionsFilterChain, times(1)).doFilter(optionsRequest, optionsResponse);
            assertThat(optionsResponse.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            assertThat(optionsResponse.getHeader("Retry-After")).isNull();
            assertThat(optionsResponse.getContentAsString()).isEmpty();
        } finally {
            releaseFirstRequest.countDown();
            try {
                if (firstRequestFuture != null) {
                    firstRequestFuture.get(3, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
