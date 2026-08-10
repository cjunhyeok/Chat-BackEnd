package com.chat.socket.manager;

import com.chat.exception.CustomException;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class SpaceManagerTest {

    private SpaceManager spaceManager;

    @BeforeEach
    void init() {
        spaceManager = new SpaceManager(null);
    }

    @Nested
    @DisplayName("addSessionToSpace()")
    class AddSessionToSpace {

        @Test
        @DisplayName("Space ID가 null이면 세션 등록 시 예외가 발생한다.")
        void Space_ID가_null이면_세션_등록_시_예외가_발생한다() {
            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));

            assertThatThrownBy(() -> spaceManager.addSessionToSpace(session, null))
                    .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("Space에 세션을 등록하면 해당 Space가 자동으로 active 상태가 된다.")
        void Space에_세션을_등록하면_해당_Space가_active_상태가_된다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getId()).willReturn("session-1");
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(session);
            spaceManager.addSessionToSpace(session, chatRoomId);

            assertThat(spaceManager.isInSpace(chatRoomId, memberId)).isTrue();
            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isTrue();
        }

        @Test
        @DisplayName("다른 Space로 전환하면 이전 Space의 active 상태가 자동으로 해제된다.")
        void 다른_Space로_전환하면_이전_Space의_active_상태가_해제된다() {
            Long room1 = 1L;
            Long room2 = 2L;
            Long memberId = 10L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getId()).willReturn("session-1");
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(session);
            spaceManager.addSessionToSpace(session, room1);   // auto-activate room1
            assertThat(spaceManager.isSpaceActive(memberId, room1)).isTrue();

            // room2로 이동
            spaceManager.addSessionToSpace(session, room2);

            assertThat(spaceManager.isSpaceActive(memberId, room1)).isFalse();  // room1 deactivated
            assertThat(spaceManager.isSpaceActive(memberId, room2)).isTrue();   // room2 activated

            assertThat(spaceManager.getWebSocketSessionBy(room1)).doesNotContain(session);  // room1 구독 해제
            assertThat(spaceManager.getWebSocketSessionBy(room2)).contains(session);        // room2 구독 등록
        }
    }

    @Nested
    @DisplayName("activateSpace()/deactivateSpace()")
    class ActivateDeactivateSpace {

        @Test
        @DisplayName("Space를 비활성화하면 active 상태가 해제된다.")
        void Space를_비활성화하면_active_상태가_해제된다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getId()).willReturn("session-1");
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(session);
            spaceManager.addSessionToSpace(session, chatRoomId);    // auto-activate 됨
            spaceManager.deactivateSpace("session-1", chatRoomId);  // deactivate

            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isFalse();
        }

        @Test
        @DisplayName("activeRoomId와 다른 Space를 비활성화 요청해도 기존 active 상태는 유지된다.")
        void activeRoomId와_다른_Space를_비활성화해도_기존_active_상태는_유지된다() {
            Long room1 = 1L;
            Long room2 = 2L;
            Long memberId = 10L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getId()).willReturn("session-1");
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(session);
            spaceManager.addSessionToSpace(session, room1);   // auto-activate room1
            assertThat(spaceManager.isSpaceActive(memberId, room1)).isTrue();

            spaceManager.deactivateSpace("session-1", room2);  // room1과 무관한 room2에 대한 비활성화 요청

            assertThat(spaceManager.isSpaceActive(memberId, room1)).isTrue();
        }

        @Test
        @DisplayName("비활성화된 Space를 다시 활성화하면 active 상태가 복구된다.")
        void 비활성화된_Space를_다시_활성화하면_active_상태가_복구된다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getId()).willReturn("session-1");
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(session);
            spaceManager.addSessionToSpace(session, chatRoomId);   // auto-activate
            spaceManager.deactivateSpace("session-1", chatRoomId); // ROOM_INACTIVE
            spaceManager.activateSpace("session-1", chatRoomId);   // ROOM_ACTIVE (복구)

            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isTrue();
        }

        @Test
        @DisplayName("Space에 등록되지 않은 세션은 Space를 active 상태로 만들 수 없다.")
        void Space에_등록되지_않은_세션은_Space를_active_상태로_만들_수_없다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getId()).willReturn("session-1");
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(session);
            // addSessionToSpace 호출 안 함 — 방에 없음

            spaceManager.activateSpace("session-1", chatRoomId);

            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isFalse();
        }
    }

    @Nested
    @DisplayName("removeSpaceSession()/removeSessionFromSpace()")
    class RemoveSession {

        @Test
        @DisplayName("존재하지 않는 Space에서 세션을 제거하면 false를 반환하고 예외가 발생하지 않는다.")
        void 존재하지_않는_Space에서_세션을_제거하면_false를_반환하고_예외가_발생하지_않는다() {
            Long chatRoomId = 999L;
            Long memberId = 42L;

            WebSocketSession mockSession = mock(WebSocketSession.class);
            given(mockSession.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            boolean result = spaceManager.removeSpaceSession(chatRoomId, mockSession);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("removeSessionFromSpace를 호출하면 세션이 속한 Space에서 구독이 제거된다.")
        void removeSessionFromSpace를_호출하면_세션이_속한_Space에서_구독이_제거된다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getId()).willReturn("session-1");
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(session);
            spaceManager.addSessionToSpace(session, chatRoomId);

            spaceManager.removeSessionFromSpace(session);

            assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).doesNotContain(session);
            assertThat(spaceManager.isInSpace(chatRoomId, memberId)).isFalse();
        }

        @Test
        @DisplayName("동일 멤버의 여러 Session 중 하나만 제거되면 false를, 마지막 Session이 제거되면 true를 반환한다.")
        void 동일_멤버의_여러_Session_중_하나만_제거되면_false를_마지막이면_true를_반환한다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession sessionA = mock(WebSocketSession.class);
            given(sessionA.getId()).willReturn("session-a");
            given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            WebSocketSession sessionB = mock(WebSocketSession.class);
            given(sessionB.getId()).willReturn("session-b");
            given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(sessionA);
            spaceManager.addSessionToSpace(sessionA, chatRoomId);

            spaceManager.registerSession(sessionB);
            spaceManager.addSessionToSpace(sessionB, chatRoomId);

            boolean resultA = spaceManager.removeSpaceSession(chatRoomId, sessionA);

            assertThat(resultA).isFalse();
            assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).contains(sessionB);
            assertThat(spaceManager.isInSpace(chatRoomId, memberId)).isTrue();

            boolean resultB = spaceManager.removeSpaceSession(chatRoomId, sessionB);

            assertThat(resultB).isTrue();
            assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).isEmpty();
            assertThat(spaceManager.isInSpace(chatRoomId, memberId)).isFalse();
        }
    }

    @Nested
    @DisplayName("SessionState 및 다중 세션 시나리오")
    class SessionState {

        @Test
        @DisplayName("세션 상태를 제거하면 해당 Space의 active 상태가 해제된다.")
        void 세션_상태를_제거하면_해당_Space의_active_상태가_해제된다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getId()).willReturn("session-1");
            given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(session);
            spaceManager.addSessionToSpace(session, chatRoomId);   // auto-activate
            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isTrue();

            spaceManager.removeSessionState(session);

            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isFalse();
        }

        @Test
        @DisplayName("다중 세션 중 하나라도 active 상태이면 Space가 active로 간주된다.")
        void 다중_세션_중_하나라도_active이면_Space가_active_상태다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession sessionA = mock(WebSocketSession.class);
            given(sessionA.getId()).willReturn("session-a");
            given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            WebSocketSession sessionB = mock(WebSocketSession.class);
            given(sessionB.getId()).willReturn("session-b");
            given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(sessionA);
            spaceManager.addSessionToSpace(sessionA, chatRoomId);   // auto-activate A

            spaceManager.registerSession(sessionB);
            spaceManager.addSessionToSpace(sessionB, chatRoomId);   // auto-activate B
            spaceManager.deactivateSpace("session-b", chatRoomId);  // B를 inactive로 전환

            // A만 active인 상태
            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isTrue();
        }

        @Test
        @DisplayName("SessionState 제거 후 다른 세션의 active 상태가 유지된다.")
        void SessionState_제거_후_다른_세션의_active_상태가_유지된다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession sessionA = mock(WebSocketSession.class);
            given(sessionA.getId()).willReturn("session-a");
            given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            WebSocketSession sessionB = mock(WebSocketSession.class);
            given(sessionB.getId()).willReturn("session-b");
            given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            spaceManager.registerSession(sessionA);
            spaceManager.addSessionToSpace(sessionA, chatRoomId);   // auto-activate A

            spaceManager.registerSession(sessionB);
            spaceManager.addSessionToSpace(sessionB, chatRoomId);   // auto-activate B

            // sessionB의 SessionState만 제거됨 (sessionA는 active 유지)
            spaceManager.removeSessionState(sessionB);

            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isTrue();
        }

        @Test
        @DisplayName("다중 세션이 모두 inactive 상태이면 Space는 active로 간주되지 않는다.")
        void 다중_세션이_모두_inactive이면_Space가_active_상태가_아니다() {
            Long chatRoomId = 1L;
            Long memberId = 10L;

            WebSocketSession sessionA = mock(WebSocketSession.class);
            given(sessionA.getId()).willReturn("session-a");
            given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            WebSocketSession sessionB = mock(WebSocketSession.class);
            given(sessionB.getId()).willReturn("session-b");
            given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

            // sessionA: add → auto-activate → deactivate
            spaceManager.registerSession(sessionA);
            spaceManager.addSessionToSpace(sessionA, chatRoomId);
            spaceManager.deactivateSpace("session-a", chatRoomId);

            // sessionB: add → auto-activate → deactivate
            spaceManager.registerSession(sessionB);
            spaceManager.addSessionToSpace(sessionB, chatRoomId);
            spaceManager.deactivateSpace("session-b", chatRoomId);

            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isFalse();
        }
    }

    @Nested
    @DisplayName("getWebSocketSessionBy()/isInSpace()")
    class Query {

        @Test
        @DisplayName("존재하지 않는 Space를 조회하면 빈 세션 목록을 반환한다.")
        void 존재하지_않는_Space를_조회하면_빈_세션_목록을_반환한다() {
            Long chatRoomId = 999L;

            Set<WebSocketSession> result = spaceManager.getWebSocketSessionBy(chatRoomId);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 Space의 멤버 참여 여부를 조회하면 false를 반환한다.")
        void 존재하지_않는_Space의_멤버_참여_여부를_조회하면_false를_반환한다() {
            // given
            Long chatRoomId = 999L;
            Long memberId = 10L;

            // when & then
            assertThat(spaceManager.isInSpace(chatRoomId, memberId)).isFalse();
        }
    }
}
