package com.chat.service.utils;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.*;

class BcryptPasswordEncoderTest {

    private BcryptPasswordEncoder encoder;

    private static final String RAW_PASSWORD = "testPassword";
    private String encodedPassword;

    @BeforeEach
    void setUp() {
        encoder = new BcryptPasswordEncoder(new SimpleMeterRegistry());
        // @Value 주입 대신 직접 설정 (permits=1로 Semaphore 동작 검증 용이)
        ReflectionTestUtils.setField(encoder, "permits", 1);
        // @PostConstruct 직접 호출
        encoder.init();

        encodedPassword = BCrypt.hashpw(RAW_PASSWORD, BCrypt.gensalt());
    }

    @Test
    @DisplayName("올바른 비밀번호는 true를 반환한다.")
    void 올바른_비밀번호는_true를_반환한다() {
        assertThat(encoder.match(RAW_PASSWORD, encodedPassword)).isTrue();
    }

    @Test
    @DisplayName("틀린 비밀번호는 false를 반환한다.")
    void 틀린_비밀번호는_false를_반환한다() {
        assertThat(encoder.match("wrongPassword", encodedPassword)).isFalse();
    }

    @Test
    @DisplayName("Semaphore 슬롯이 모두 사용 중이면 SERVER_BUSY 예외가 발생한다.")
    void Semaphore가_고갈되면_SERVER_BUSY_예외가_발생한다() throws InterruptedException {
        // given: permits=1인 Semaphore를 수동으로 소진
        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(encoder, "semaphore");
        semaphore.acquire();

        try {
            // when & then: 슬롯 없으므로 tryAcquire 실패 → SERVER_BUSY
            assertThatThrownBy(() -> encoder.match(RAW_PASSWORD, encodedPassword))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SERVER_BUSY));
        } finally {
            semaphore.release();
        }
    }

    @Test
    @DisplayName("match 완료 후 Semaphore 슬롯이 반납된다.")
    void match_완료_후_Semaphore_슬롯이_반납된다() {
        // given: permits=1이므로 첫 번째 호출이 슬롯을 점유했다가 반납
        encoder.match(RAW_PASSWORD, encodedPassword);

        // when & then: 슬롯이 반납되었으므로 두 번째 호출도 성공
        assertThatCode(() -> encoder.match(RAW_PASSWORD, encodedPassword))
                .doesNotThrowAnyException();
    }
}
