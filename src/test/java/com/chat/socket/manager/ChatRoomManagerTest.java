package com.chat.socket.manager;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.service.dtos.chat.EnterChatRoom;
import com.chat.utils.consts.SessionConst;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@SpringBootTest
class ChatRoomManagerTest {

    @Autowired
    private ChatRoomManager chatRoomManager;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void init() {
        chatRoomManager.clearAll();
    }

    @Test
    @DisplayName("채팅방 세션을 추가한다.")
    void addSessionToRoomTest() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 10L;
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        // when
        chatRoomManager.addSessionToRoom(session, chatRoomId);

        // then
        Set<WebSocketSession> sessionsInRoom = chatRoomManager.getWebSocketSessionBy(chatRoomId);
        assertThat(sessionsInRoom).contains(session);
        Set<Long> roomsOfMember = chatRoomManager.getChatRoomIdsBy(memberId);
        assertThat(roomsOfMember).contains(chatRoomId);
    }

    @Test
    @DisplayName("여러 세션을 동일 채팅방에 추가할 수 있다")
    void addMultipleSessionsToRoomTest() {
        Long chatRoomId = 1L;

        WebSocketSession session1 = mock(WebSocketSession.class);
        given(session1.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));

        WebSocketSession session2 = mock(WebSocketSession.class);
        given(session2.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 2L));

        chatRoomManager.addSessionToRoom(session1, chatRoomId);
        chatRoomManager.addSessionToRoom(session2, chatRoomId);

        Set<WebSocketSession> sessions = chatRoomManager.getWebSocketSessionBy(chatRoomId);
        assertThat(sessions).contains(session1, session2);
    }

    @Test
    @DisplayName("채팅방 ID가 유효하지 않으면 예외 발생")
    void invalidChatRoomIdTest() {
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));

        assertThatThrownBy(() -> chatRoomManager.addSessionToRoom(session, null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("채팅방에 세션이 존재하면 해당 세션 세트를 반환한다")
    void getWebSocketSessionByTest() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 100L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);

        // when
        Set<WebSocketSession> result = chatRoomManager.getWebSocketSessionBy(chatRoomId);

        // then
        assertThat(result).isNotNull();
        assertThat(result).contains(session);
    }

    @Test
    @DisplayName("채팅방이 존재하지 않으면 CustomException 발생")
    void nonExistentRoomTest() {
        Long chatRoomId = 999L;

        assertThatThrownBy(() -> chatRoomManager.getWebSocketSessionBy(chatRoomId))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex)
                .satisfies(ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WEB_SOCKET_SESSION_NOT_EXIST);
                });
    }

    @Test
    @DisplayName("채팅방이 있지만 세션이 없으면 CustomException 발생")
    void emptySessionSetTest() {
        Long chatRoomId = 999L;
        Long memberId = 42L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);
        chatRoomManager.removeChatRoomSession(chatRoomId, memberId);

        assertThatThrownBy(() -> chatRoomManager.getWebSocketSessionBy(chatRoomId))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex)
                .satisfies(ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WEB_SOCKET_SESSION_NOT_EXIST);
                });
    }

    @Test
    @DisplayName("해당 멤버가 참여한 채팅방 ID 반환")
    void getChatRoomIdsByTest() {
        // given
        Long chatRoomId1 = 1L;
        Long chatRoomId2 = 2L;
        Long memberId = 42L;

        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);

        given(session1.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));
        given(session2.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        // when
        chatRoomManager.addSessionToRoom(session1, chatRoomId1);
        chatRoomManager.addSessionToRoom(session2, chatRoomId2);

        // then
        Set<Long> chatRoomIds = chatRoomManager.getChatRoomIdsBy(memberId);
        assertThat(chatRoomIds).isNotNull();
        assertThat(chatRoomIds).contains(chatRoomId1, chatRoomId2);
    }

    @Test
    @DisplayName("해당 멤버가 참여한 채팅방이 없으면 null 반환")
    void noRoomsTest() {
        Long memberId = 99L; // 참여한 방 없음

        Set<Long> chatRoomIds = chatRoomManager.getChatRoomIdsBy(memberId);
        assertThat(chatRoomIds).isNull();
    }

    @Test
    @DisplayName("정상적으로 특정 멤버 세션 제거")
    void removeChatRoomSessionTest() {
        Long chatRoomId = 1L;
        Long memberId = 42L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);

        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).contains(session);
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).contains(chatRoomId);

        chatRoomManager.removeChatRoomSession(chatRoomId, memberId);

        assertThatThrownBy(() -> chatRoomManager.getWebSocketSessionBy(chatRoomId))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex)
                .satisfies(ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WEB_SOCKET_SESSION_NOT_EXIST);
                });

        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).isNull();
    }

    @Test
    @DisplayName("채팅방이 없으면 아무 동작도 하지 않는다.")
    void removeChatRoomSession_noRoomTest() {
        Long chatRoomId = 999L;
        Long memberId = 42L;

        assertThatCode(() -> chatRoomManager.removeChatRoomSession(chatRoomId, memberId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("멤버의 세션이 없으면 아무 동작도 하지 않는다.")
    void removeChatRoomSession_noMemberSessionTest() {
        Long chatRoomId = 1L;
        Long memberId = 42L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 99L)); // 다른 멤버

        chatRoomManager.addSessionToRoom(session, chatRoomId);

        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).contains(session);

        chatRoomManager.removeChatRoomSession(chatRoomId, memberId);

        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).contains(session);
    }

    @Test
    @DisplayName("멤버와 채팅방 모두 제거")
    void removeChatRoomSession_cleanUpTest() {
        Long chatRoomId = 1L;
        Long memberId = 42L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);

        chatRoomManager.removeChatRoomSession(chatRoomId, memberId);

        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).isNull();
    }

    @Test
    @DisplayName("채팅방의 모든 세션에 입장 메시지를 브로드캐스트한다.")
    void broadcastEnterChatRoomTest() throws IOException {
        // given
        Long chatRoomId = 1L;
        WebSocketSession session1 = mock(WebSocketSession.class);
        given(session1.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));
        WebSocketSession session2 = mock(WebSocketSession.class);
        given(session2.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 2L));

        // 테스트용 채팅방 등록
        chatRoomManager.addSessionToRoom(session1, chatRoomId);
        chatRoomManager.addSessionToRoom(session2, chatRoomId);

        EnterChatRoom enterChatRoom = new EnterChatRoom(chatRoomId, 1L);

        // when
        chatRoomManager.broadcastEnterChatRoom(chatRoomId, enterChatRoom);

        // then - 각 세션에 sendMessage()가 한 번씩 호출되어야 함
        verify(session1, times(1)).sendMessage(any(TextMessage.class));
        verify(session2, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("메시지 전송 중 IOException 발생 시 CustomException 이 발생한다.")
    void broadcastEnterChatRoom_ioException() throws IOException {
        // given
        Long chatRoomId = 1L;
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));

        doThrow(new IOException("send fail")).when(session).sendMessage(any(TextMessage.class));

        chatRoomManager.addSessionToRoom(session, chatRoomId);
        EnterChatRoom enterChatRoom = new EnterChatRoom(chatRoomId, 1L);

        // when & then
        assertThatThrownBy(() -> chatRoomManager.broadcastEnterChatRoom(chatRoomId, enterChatRoom))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex)
                .satisfies(ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CHAT_ROOM_BROADCAST_IO_EXCEPTION);
                });
    }

    @Test
    @DisplayName("메시지의 실제 JSON 내용이 올바르게 브로드캐스트된다.")
    void broadcastEnterChatRoom_verifyMessageContent() throws Exception {
        // given
        Long chatRoomId = 1L;
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));
        chatRoomManager.addSessionToRoom(session, chatRoomId);

        EnterChatRoom message = new EnterChatRoom(chatRoomId, 1L);

        // when
        chatRoomManager.broadcastEnterChatRoom(chatRoomId, message);

        // then - 실제 전송된 메시지 내용 검증
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(captor.capture());

        String sentPayload = captor.getValue().getPayload();
        String expectedJson = objectMapper.writeValueAsString(message);

        assertThat(sentPayload).isEqualTo(expectedJson);
    }
}