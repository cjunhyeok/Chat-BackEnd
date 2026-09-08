package com.chat.socket.listener;

import com.chat.service.dtos.chat.ReadEventBatch;
import com.chat.service.dtos.chat.RoomMessageSummaryUpdated;
import com.chat.service.dtos.chat.SpaceInvited;
import com.chat.service.dtos.chat.SpaceTitleChanged;
import com.chat.socket.event.PublishDiscussionMessageEvent;
import com.chat.socket.event.PublishMessageEvent;
import com.chat.socket.event.PublishReadEvent;
import com.chat.socket.event.PublishSpaceInvitedEvent;
import com.chat.socket.event.PublishSpaceTitleChangedEvent;
import com.chat.socket.manager.ReadEventBatchAccumulator;
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
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBroadcastListener {

    private final SpaceManager spaceManager;
    private final WebsocketSessionManager websocketSessionManager;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ReadEventBatchAccumulator readEventBatchAccumulator;

    @Async("broadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessagePublished(PublishMessageEvent event) {
        long afterCommitDelayNanos = System.nanoTime() - event.getPublishedAtNanos();
        meterRegistry.timer(MessageMetricNames.MESSAGE_AFTER_COMMIT_DELAY)
                .record(Duration.ofNanos(afterCommitDelayNanos));

        Timer.Sample chatBroadcastSample = Timer.start(meterRegistry);
        try {
            sendToSpaceSessions(event.getChatRoomId(), event.getBroadcastChat());
        } finally {
            chatBroadcastSample.stop(meterRegistry.timer(MessageMetricNames.MESSAGE_BROADCAST_CHAT_DURATION));
        }

        RoomMessageSummaryUpdated roomMessageSummaryUpdated = event.getRoomMessageSummaryUpdated();
        if (roomMessageSummaryUpdated != null) {
            sendToRecipientMemberSessions(event.getRecipientMemberIds(), roomMessageSummaryUpdated, roomMessageSummaryUpdated.getChatRoomId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadEventPublished(PublishReadEvent event) {
        readEventBatchAccumulator.enqueue(event);
    }

    @Async("broadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSpaceTitleChanged(PublishSpaceTitleChangedEvent event) {
        SpaceTitleChanged payload = event.getSpaceTitleChanged();
        sendToRecipientMemberSessions(event.getRecipientMemberIds(), payload, payload.getChatRoomId());
    }

    @Async("broadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSpaceInvited(PublishSpaceInvitedEvent event) {
        SpaceInvited payload = event.getSpaceInvited();
        sendToRecipientMemberSessions(event.getRecipientMemberIds(), payload, event.getChatRoomId());
    }

    @Async("broadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscussionMessagePublished(PublishDiscussionMessageEvent event) {
        sendToSpaceSessions(event.getSpaceId(), event.getPayload());
    }

    @Async("broadcastExecutor")
    public void broadcastReadEventBatch(ReadEventBatch batch) {
        sendToSpaceSessions(batch.getChatRoomId(), batch);
    }

    private void sendToSpaceSessions(Long chatRoomId, Object payload) {
        sendToSessions(spaceManager.getWebSocketSessionBy(chatRoomId), payload, chatRoomId);
    }

    private void sendToRecipientMemberSessions(Set<Long> recipientMemberIds, Object payload, Long chatRoomId) {
        for (Long memberId : recipientMemberIds) {
            sendToSessions(websocketSessionManager.getSessionBy(memberId), payload, chatRoomId);
        }
    }

    private void sendToSessions(Collection<WebSocketSession> sessions, Object payload, Long chatRoomId) {
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
