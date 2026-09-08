package com.chat.socket.manager;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Collection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class WebsocketSessionManagerTest {

    private WebsocketSessionManager websocketSessionManager;

    @BeforeEach
    void init() {
        websocketSessionManager = new WebsocketSessionManager(null);
    }

    @Test
    @DisplayName("동일 회원에 여러 Session을 등록하면 getSessionBy로 모두 조회된다.")
    void 동일_회원에_여러_Session을_등록하면_getSessionBy로_모두_조회된다() {
        Long memberId = 1L;

        WebSocketSession session1 = mock(WebSocketSession.class);
        given(session1.getId()).willReturn("session-1");

        WebSocketSession session2 = mock(WebSocketSession.class);
        given(session2.getId()).willReturn("session-2");

        websocketSessionManager.addSession(memberId, session1);
        websocketSessionManager.addSession(memberId, session2);

        Collection<WebSocketSession> result = websocketSessionManager.getSessionBy(memberId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(WebSocketSession::getId)
                .containsExactlyInAnyOrder("session-1", "session-2");
    }

    @Test
    @DisplayName("등록되지 않은 회원의 Session을 조회하면 null이 아닌 빈 컬렉션을 반환한다.")
    void 등록되지_않은_회원의_Session을_조회하면_null이_아닌_빈_컬렉션을_반환한다() {
        Long memberId = 999L;

        Collection<WebSocketSession> result = websocketSessionManager.getSessionBy(memberId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("등록된 raw Session의 id로 조회하면 대응하는 wrapped Session을 반환한다.")
    void 등록된_raw_Session의_id로_조회하면_대응하는_wrapped_Session을_반환한다() {
        Long memberId = 1L;

        WebSocketSession rawSession = mock(WebSocketSession.class);
        given(rawSession.getId()).willReturn("session-1");

        websocketSessionManager.addSession(memberId, rawSession);

        WebSocketSession wrappedSession = websocketSessionManager.getWrappedSession(rawSession);

        assertThat(wrappedSession).isNotNull();
        assertThat(wrappedSession.getId()).isEqualTo(rawSession.getId());
        assertThat(wrappedSession).isNotSameAs(rawSession);
        assertThat(wrappedSession).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
    }

    @Test
    @DisplayName("등록되지 않은 Session의 id로 조회하면 WEB_SOCKET_SESSION_NOT_EXIST 예외가 발생한다.")
    void 등록되지_않은_Session의_id로_조회하면_WEB_SOCKET_SESSION_NOT_EXIST_예외가_발생한다() {
        WebSocketSession unregisteredSession = mock(WebSocketSession.class);
        given(unregisteredSession.getId()).willReturn("unknown-session");

        assertThatThrownBy(() -> websocketSessionManager.getWrappedSession(unregisteredSession))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WEB_SOCKET_SESSION_NOT_EXIST);
    }

    @Test
    @DisplayName("Session을 하나 제거해도 같은 회원의 다른 Session은 유지되고, 마지막 Session까지 제거하면 조회 결과가 비어 있다.")
    void Session을_하나_제거해도_같은_회원의_다른_Session은_유지되고_마지막_Session까지_제거하면_조회_결과가_비어있다() {
        Long memberId = 1L;

        WebSocketSession session1 = mock(WebSocketSession.class);
        given(session1.getId()).willReturn("session-1");

        WebSocketSession session2 = mock(WebSocketSession.class);
        given(session2.getId()).willReturn("session-2");

        websocketSessionManager.addSession(memberId, session1);
        websocketSessionManager.addSession(memberId, session2);

        websocketSessionManager.removeSession(memberId, session1);

        Collection<WebSocketSession> afterFirstRemoval = websocketSessionManager.getSessionBy(memberId);
        assertThat(afterFirstRemoval).hasSize(1);
        assertThat(afterFirstRemoval).extracting(WebSocketSession::getId)
                .containsExactly("session-2");

        websocketSessionManager.removeSession(memberId, session2);

        assertThat(websocketSessionManager.getSessionBy(memberId)).isEmpty();
    }
}
