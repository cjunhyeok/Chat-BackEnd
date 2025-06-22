package com.chat.socket.handler;

import com.chat.utils.consts.SessionConst;
import com.chat.socket.manager.PreviousChatRoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomTextWebSocketHandler extends TextWebSocketHandler {

    private final PreviousChatRoomManager previousChatRoomManager;

    // WebSocket 연결 시 실행
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Long chatRoomId = extractRoomId(session);
        previousChatRoomManager.addSessionToRoom(chatRoomId, session);

        log.info("Connect WebSocket");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long chatRoomId = extractRoomId(session);
        previousChatRoomManager.removeSessionFromRoom(chatRoomId, session);
        log.info("Close WebSocket");
    }

    private Long extractRoomId(WebSocketSession session) {
        String uri = session.getUri().toString();
        return Long.parseLong(uri.substring(uri.lastIndexOf("/") + 1));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Long chatRoomId = extractRoomId(session);
        Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
        String payload = message.getPayload();
        previousChatRoomManager.broadcastMessageToChatRoom(loginMemberId, chatRoomId, payload);
    }
}
