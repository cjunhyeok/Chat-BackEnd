package com.chat.socket.manager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WebsocketSessionManager {

    // 소켓에 연결된 사용자 정보
    private final Map<Long, WebSocketSession> activeMemberSessions = new ConcurrentHashMap<>();
    private final PreviousChatRoomManager previousChatRoomManager;

    public void addSession(Long memberId, WebSocketSession session) {
        activeMemberSessions.put(memberId, session);
    }

    public WebSocketSession getSessionBy(Long memberId) {
        return activeMemberSessions.get(memberId);
    }

    public void removeSession(Long memberId) {
        activeMemberSessions.remove(memberId);

        // todo 채팅방 세션 삭제 필요
    }
}
