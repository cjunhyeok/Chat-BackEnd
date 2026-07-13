package com.chat.socket.manager;

import com.chat.utils.annotation.VisibleForTesting;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.consts.WsMetricNames;
import com.chat.utils.valid.IdValidator;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpaceManager {

    private final MeterRegistry meterRegistry;
    private final Map<Long, Set<WebSocketSession>> chatRooms = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionToRoomMap = new ConcurrentHashMap<>();
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder(WsMetricNames.WS_ROOM_SESSIONS, chatRooms,
                        map -> (double) map.values().stream().mapToInt(Set::size).sum())
                .register(meterRegistry);
    }

    public void addSessionToSpace(WebSocketSession session, Long chatRoomId) {

        IdValidator.requireChatRoomId(chatRoomId);

        // 방 전환
        chatRooms.forEach((roomId, sessions) -> {
            if (!roomId.equals(chatRoomId) &&
                    sessions.stream().anyMatch(s -> s.getId().equals(session.getId()))) {

                SessionState state = sessionStates.get(session.getId());
                if (state != null) {
                    state.deactivatedIfRoom(roomId);
                } else {
                    Long memberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
                    log.warn("addSessionToSpace: no SessionState, session={}, memberId={}", session.getId(), memberId);
                }

                removeSpaceSession(roomId, session);
            }
        });

        chatRooms.computeIfAbsent(chatRoomId, key -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToRoomMap.put(session.getId(), chatRoomId);
        SessionState state = sessionStates.get(session.getId());
        if (state != null) {
            state.activate(chatRoomId);
        } else {
            Long memberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
            log.warn("addSessionToSpace: no SessionState, session={}, memberId={}", session.getId(), memberId);
        }
    }

    public boolean isInSpace(Long chatRoomId, Long memberId) {
        return getWebSocketSessionBy(chatRoomId).stream()
                .anyMatch(s -> memberId.equals(s.getAttributes().get(SessionConst.SESSION_ID)));
    }

    public Set<WebSocketSession> getWebSocketSessionBy(Long chatRoomId) {
        Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);
        if (sessions == null) {
            return Collections.emptySet();
        }
        return sessions;
    }

    public void removeSessionFromSpace(WebSocketSession session) {
        Long roomId = sessionToRoomMap.get(session.getId());
        if (roomId != null) {
            removeSpaceSession(roomId, session);
        }
    }

    public boolean removeSpaceSession(Long chatRoomId, WebSocketSession closingSession) {
        Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);
        if (sessions == null) {
            return false;
        }

        sessions.removeIf(s -> s.getId().equals(closingSession.getId()));
        sessionToRoomMap.remove(closingSession.getId());

        Long memberId = (Long) closingSession.getAttributes().get(SessionConst.SESSION_ID);
        boolean memberStillInRoom = sessions.stream()
                .anyMatch(s -> memberId.equals(s.getAttributes().get(SessionConst.SESSION_ID)));

        if (sessions.isEmpty()) {
            chatRooms.remove(chatRoomId);
        }

        return !memberStillInRoom;
    }

    public void registerSession(WebSocketSession session) {
        Long memberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
        sessionStates.put(session.getId(), new SessionState(memberId));
    }

    public void activateSpace(String sessionId, Long chatRoomId) {
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            log.warn("activateSpace: no SessionState for sessionId={}", sessionId);
            return;
        }

        Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);
        if (sessions == null ||
                sessions.stream().noneMatch(s -> s.getId().equals(sessionId))) {

            log.warn("activateSpace rejected: session={} not in room={}", sessionId, chatRoomId);
            return;
        }
        state.activate(chatRoomId);
    }

    public boolean isSpaceActive(Long memberId, Long chatRoomId) {
        return sessionStates.values().stream()
                .anyMatch(state -> memberId.equals(state.getMemberId())
                        && chatRoomId.equals(state.getActiveRoomId()));
    }

    public void deactivateSpace(String sessionId, Long chatRoomId) {
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            return;
        }
        state.deactivatedIfRoom(chatRoomId);
    }

    public void removeSessionState(WebSocketSession session) {
        sessionStates.remove(session.getId());
    }

    @VisibleForTesting
    public void clearAll() {
        chatRooms.clear();
        sessionToRoomMap.clear();
        sessionStates.clear();
    }
}
