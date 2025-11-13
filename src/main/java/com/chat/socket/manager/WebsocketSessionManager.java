package com.chat.socket.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebsocketSessionManager {

    // 소켓에 연결된 사용자 정보
    private final Map<Long, WebSocketSession> activeMemberSessions = new ConcurrentHashMap<>();

    public void addSession(Long memberId, WebSocketSession session) {
        activeMemberSessions.put(memberId, session);
    }

    public WebSocketSession getSessionBy(Long memberId) {
        return activeMemberSessions.get(memberId);
    }

    public void removeSession(Long memberId) {
        WebSocketSession session = activeMemberSessions.remove(memberId);

        if (session == null) {
            return;
        }

        if (session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.warn("Failed to close WebSocket session for memberId={}", memberId, e);
            }
        }
    }
}
