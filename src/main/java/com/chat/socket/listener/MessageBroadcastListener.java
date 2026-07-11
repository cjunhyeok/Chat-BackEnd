package com.chat.socket.listener;

import com.chat.service.dtos.chat.ReadEvent;
import com.chat.service.dtos.chat.RoomMessageSummaryUpdated;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.socket.event.PublishDiscussionMessageEvent;
import com.chat.socket.event.PublishMessageEvent;
import com.chat.socket.event.PublishReadEvent;
import com.chat.socket.event.PublishUpdateEvent;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.MessageMetricNames;
import com.chat.utils.consts.SessionConst;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBroadcastListener {

    private final SpaceManager spaceManager;
    private final WebsocketSessionManager websocketSessionManager;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Async("broadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageToSessions(PublishMessageEvent event) {
        long afterCommitDelayNanos = System.nanoTime() - event.getPublishedAtNanos();
        meterRegistry.timer(MessageMetricNames.MESSAGE_AFTER_COMMIT_DELAY)
                .record(Duration.ofNanos(afterCommitDelayNanos));

        Timer.Sample chatBroadcastSample = Timer.start(meterRegistry);
        try {
            sendToSpaceSessions(event.getChatRoomId(), event.getBroadcastChat());
        } finally {
            chatBroadcastSample.stop(meterRegistry.timer(MessageMetricNames.MESSAGE_BROADCAST_CHAT_DURATION));
        }

        sendRoomMessageSummaryUpdated(event.getRoomMessageSummaryUpdated(), event.getRecipientMemberIds());
    }

    @Async("broadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishReadEventToSessions(PublishReadEvent event) {
        ReadEvent readEvent = new ReadEvent(
                event.getMemberId(),
                event.getChatRoomId(),
                event.getPreviousLastReadChatId(),
                event.getCurrentLastReadChatId()
        );

        Timer.Sample readEventBroadcastSample = Timer.start(meterRegistry);
        try {
            sendToSpaceSessions(event.getChatRoomId(), readEvent);
        } finally {
            readEventBroadcastSample.stop(meterRegistry.timer(MessageMetricNames.READ_EVENT_BROADCAST_DURATION));
        }
    }

    @Async("broadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishUpdateEventToSessions(PublishUpdateEvent event) {
        sendUpdateChatRoom(event.getUpdatesByMemberId());
    }

    @Async("broadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDiscussionMessageToSessions(PublishDiscussionMessageEvent event) {
        sendToSpaceSessions(event.getSpaceId(), event.getPayload());
    }

    private void sendToSpaceSessions(Long chatRoomId, Object payload) {
        send(spaceManager.getWebSocketSessionBy(chatRoomId), payload, chatRoomId);
    }

    private void sendUpdateChatRoom(Map<Long, UpdateChatRoom> updatesByMemberId) {
        updatesByMemberId
                .forEach((memberId, updateChatRoom) -> {
                    send(websocketSessionManager.getSessionBy(memberId), updateChatRoom, updateChatRoom.getChatRoomId());
                });
    }

    private void sendRoomMessageSummaryUpdated(RoomMessageSummaryUpdated roomMessageSummaryUpdated, Set<Long> targetMemberIds) {
        if (roomMessageSummaryUpdated == null) {
            return;
        }

        for (Long memberId : targetMemberIds) {
            send(websocketSessionManager.getSessionBy(memberId), roomMessageSummaryUpdated, roomMessageSummaryUpdated.getChatRoomId());
        }
    }

    private void send(Collection<WebSocketSession> sessions, Object payload, Long chatRoomId) {
        if (sessions.isEmpty()) {
            return;
        }

        String message;
        try {
            message = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.error("Broadcast 직렬화 실패: eventType={}, chatRoomId={}", payload.getClass().getSimpleName(), chatRoomId, e);
            return;
        }

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                Long memberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
                log.warn("Broadcast 전송 실패: eventType={}, chatRoomId={}, session={}, memberId={}",
                        payload.getClass().getSimpleName(), chatRoomId, session.getId(), memberId, e);
            }
        }
    }
}
