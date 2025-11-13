package com.chat.socket.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest
class WebsocketSessionManagerTest {

    @Autowired
    private WebsocketSessionManager sessionManager;

    @Test
    @DisplayName("세션을 추가하고 memberId 로 조회한다.")
    void getSessionTest() {
        // given
        Long memberId = 1L;
        WebSocketSession mockSession = mock(WebSocketSession.class);

        // when
        sessionManager.addSession(memberId, mockSession);
        WebSocketSession session = sessionManager.getSessionBy(memberId);

        // then
        assertThat(session).isNotNull();
        assertThat(session).isEqualTo(mockSession);
    }

    @Test
    @DisplayName("세션을 삭제하면 조회 시 null 을 반화한다.")
    void removeSessionTest() {
        // given
        Long memberId = 1L;
        WebSocketSession mockSession = mock(WebSocketSession.class);
        sessionManager.addSession(memberId, mockSession);

        // when
        sessionManager.removeSession(memberId);
        WebSocketSession session = sessionManager.getSessionBy(memberId);

        // then
        assertThat(session).isNull();
    }

    @Test
    @DisplayName("ConcurrentHashMap 기반의 세션 관리가 다중 스레드 환경에서도 안전하게 동작한다.")
    void concurrentAccessTest() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        IntStream.rangeClosed(1, threadCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long memberId = (long) i;
                    WebSocketSession session = mock(WebSocketSession.class);

                    // 여러 스레드에서 동시에 add, get, remove 수행
                    sessionManager.addSession(memberId, session);
                    WebSocketSession found = sessionManager.getSessionBy(memberId);
                    assertThat(found).isEqualTo(session);

                    sessionManager.removeSession(memberId);
                    WebSocketSession removed = sessionManager.getSessionBy(memberId);
                    assertThat(removed).isNull();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(); // 모든 스레드가 종료될 때까지 대기
        executorService.shutdown();

        // 최종적으로 activeMemberSessions 는 비어 있어야 함
        assertThat(sessionManager.getSessionBy(1L)).isNull();
        assertThat(sessionManager.getSessionBy(100L)).isNull();
    }

    @Test
    @DisplayName("대량의 동시 요청에서도 ConcurrentHashMap 기반 세션 관리가 안정적으로 동작하고 성능을 보장한다.")
    void concurrentPerformanceTest() throws InterruptedException {
        int threadCount = 200;   // 동시에 실행할 스레드 수
        int iterations = 1000;   // 각 스레드에서 반복 실행 횟수
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.nanoTime();

        IntStream.rangeClosed(1, threadCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long memberId = (long) i;
                    for (int j = 0; j < iterations; j++) {
                        WebSocketSession session = mock(WebSocketSession.class);
                        sessionManager.addSession(memberId, session);

                        WebSocketSession found = sessionManager.getSessionBy(memberId);
                        assertThat(found).isEqualTo(session);

                        sessionManager.removeSession(memberId);
                        WebSocketSession removed = sessionManager.getSessionBy(memberId);
                        assertThat(removed).isNull();
                    }
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executorService.shutdown();

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        long totalOps = (long) threadCount * iterations * 3; // add/get/remove = 3 ops

        System.out.printf("총 실행 시간: %d ms%n", durationMs);
        System.out.printf("총 연산 수: %d ops%n", totalOps);
        System.out.printf("초당 처리량: %d ops/sec%n", (totalOps * 1000) / durationMs);

        // 최종적으로 Map 은 비어 있어야 함
        assertThat(sessionManager.getSessionBy(1L)).isNull();
        assertThat(sessionManager.getSessionBy((long) threadCount)).isNull();
    }
}