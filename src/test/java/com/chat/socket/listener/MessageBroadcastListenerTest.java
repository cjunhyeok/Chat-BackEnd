package com.chat.socket.listener;

import com.chat.service.dtos.chat.BroadcastChat;
import com.chat.service.dtos.chat.DiscussionMessageEvent;
import com.chat.service.dtos.chat.ReadEventBatch;
import com.chat.service.dtos.chat.ReadEventBatchItem;
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
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MessageBroadcastListenerTest {

    private SpaceManager spaceManager;
    private WebsocketSessionManager websocketSessionManager;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private ReadEventBatchAccumulator readEventBatchAccumulator;

    private MessageBroadcastListener messageBroadcastListener;

    @BeforeEach
    void init() {
        spaceManager = mock(SpaceManager.class);
        websocketSessionManager = mock(WebsocketSessionManager.class);
        objectMapper = mock(ObjectMapper.class);
        meterRegistry = new SimpleMeterRegistry();
        readEventBatchAccumulator = mock(ReadEventBatchAccumulator.class);

        messageBroadcastListener = new MessageBroadcastListener(
                spaceManager,
                websocketSessionManager,
                objectMapper,
                meterRegistry,
                readEventBatchAccumulator
        );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Nested
    @DisplayName("onMessagePublished")
    class OnMessagePublished {

        @Test
        @DisplayName("onMessagePublished는 CHAT_MESSAGE를 Space 세션에, summary를 recipient member 세션에 전송한다.")
        void onMessagePublished는_CHAT_MESSAGE를_Space_세션에_summary를_recipient_세션에_전송한다() throws IOException {
            // given
            Long chatRoomId = 1L;
            Long recipientMemberId = 20L;

            WebSocketSession roomSession = mock(WebSocketSession.class);
            given(roomSession.isOpen()).willReturn(true);

            WebSocketSession recipientSession = mock(WebSocketSession.class);
            given(recipientSession.isOpen()).willReturn(true);

            given(spaceManager.getWebSocketSessionBy(chatRoomId)).willReturn(Set.of(roomSession));
            given(websocketSessionManager.getSessionBy(recipientMemberId)).willReturn(Set.of(recipientSession));

            BroadcastChat broadcastChat = BroadcastChat.builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(chatRoomId)
                    .build();
            RoomMessageSummaryUpdated roomMessageSummaryUpdated = RoomMessageSummaryUpdated.builder()
                    .messageType(MessageType.ROOM_MESSAGE_SUMMARY_UPDATED)
                    .chatRoomId(chatRoomId)
                    .build();

            given(objectMapper.writeValueAsString(any(BroadcastChat.class)))
                    .willReturn("{\"messageType\":\"CHAT_MESSAGE\"}");
            given(objectMapper.writeValueAsString(any(RoomMessageSummaryUpdated.class)))
                    .willReturn("{\"messageType\":\"ROOM_MESSAGE_SUMMARY_UPDATED\"}");

            PublishMessageEvent event = new PublishMessageEvent(
                    broadcastChat, chatRoomId, roomMessageSummaryUpdated, Set.of(recipientMemberId), System.nanoTime());

            // when
            messageBroadcastListener.onMessagePublished(event);

            // then
            verify(spaceManager).getWebSocketSessionBy(chatRoomId);
            verify(websocketSessionManager).getSessionBy(recipientMemberId);

            ArgumentCaptor<TextMessage> roomMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(roomSession).sendMessage(roomMessageCaptor.capture());
            assertThat(roomMessageCaptor.getValue().getPayload()).isEqualTo("{\"messageType\":\"CHAT_MESSAGE\"}");

            ArgumentCaptor<TextMessage> recipientMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(recipientSession).sendMessage(recipientMessageCaptor.capture());
            assertThat(recipientMessageCaptor.getValue().getPayload()).isEqualTo("{\"messageType\":\"ROOM_MESSAGE_SUMMARY_UPDATED\"}");
        }
    }

    @Nested
    @DisplayName("onReadEventPublished")
    class OnReadEventPublished {

        @Test
        @DisplayName("onReadEventPublished는 즉시 전송 없이 accumulator에 enqueue만 한다.")
        void onReadEventPublished는_accumulator에_enqueue만_한다() {
            PublishReadEvent event = new PublishReadEvent(10L, 1L, 100L, 110L);

            messageBroadcastListener.onReadEventPublished(event);

            verify(readEventBatchAccumulator).enqueue(event);
            verifyNoInteractions(spaceManager, websocketSessionManager, objectMapper);
        }
    }

    @Nested
    @DisplayName("onSpaceTitleChanged")
    class OnSpaceTitleChanged {

        @Test
        @DisplayName("onSpaceTitleChanged는 recipient member 세션에 SPACE_TITLE_CHANGED를 전송한다.")
        void onSpaceTitleChanged는_recipient_member_세션에_SPACE_TITLE_CHANGED를_전송한다() throws IOException {
            // given
            Long chatRoomId = 1L;
            Long recipientMemberId = 30L;

            WebSocketSession recipientSession = mock(WebSocketSession.class);
            given(recipientSession.isOpen()).willReturn(true);
            given(websocketSessionManager.getSessionBy(recipientMemberId)).willReturn(Set.of(recipientSession));

            SpaceTitleChanged spaceTitleChanged = SpaceTitleChanged.builder()
                    .messageType(MessageType.SPACE_TITLE_CHANGED)
                    .chatRoomId(chatRoomId)
                    .build();

            given(objectMapper.writeValueAsString(any(SpaceTitleChanged.class)))
                    .willReturn("{\"messageType\":\"SPACE_TITLE_CHANGED\"}");

            PublishSpaceTitleChangedEvent event =
                    new PublishSpaceTitleChangedEvent(spaceTitleChanged, Set.of(recipientMemberId));

            // when
            messageBroadcastListener.onSpaceTitleChanged(event);

            // then
            verify(websocketSessionManager).getSessionBy(recipientMemberId);
            verifyNoInteractions(spaceManager);

            ArgumentCaptor<TextMessage> textMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(recipientSession).sendMessage(textMessageCaptor.capture());
            assertThat(textMessageCaptor.getValue().getPayload()).isEqualTo("{\"messageType\":\"SPACE_TITLE_CHANGED\"}");
        }
    }

    @Nested
    @DisplayName("onSpaceInvited")
    class OnSpaceInvited {

        @Test
        @DisplayName("onSpaceInvited는 모든 recipient member 세션에 SPACE_INVITED를 전송한다.")
        void onSpaceInvited는_모든_recipient_member_세션에_SPACE_INVITED를_전송한다() throws IOException {
            // given
            Long chatRoomId = 2L;
            Long recipientMemberIdA = 40L;
            Long recipientMemberIdB = 41L;

            WebSocketSession recipientSessionA = mock(WebSocketSession.class);
            given(recipientSessionA.isOpen()).willReturn(true);
            given(websocketSessionManager.getSessionBy(recipientMemberIdA)).willReturn(Set.of(recipientSessionA));

            WebSocketSession recipientSessionB = mock(WebSocketSession.class);
            given(recipientSessionB.isOpen()).willReturn(true);
            given(websocketSessionManager.getSessionBy(recipientMemberIdB)).willReturn(Set.of(recipientSessionB));

            SpaceInvited spaceInvited = SpaceInvited.builder()
                    .messageType(MessageType.SPACE_INVITED)
                    .build();

            given(objectMapper.writeValueAsString(any(SpaceInvited.class)))
                    .willReturn("{\"messageType\":\"SPACE_INVITED\"}");

            PublishSpaceInvitedEvent event = new PublishSpaceInvitedEvent(
                    spaceInvited, chatRoomId, Set.of(recipientMemberIdA, recipientMemberIdB));

            // when
            messageBroadcastListener.onSpaceInvited(event);

            // then
            verify(websocketSessionManager).getSessionBy(recipientMemberIdA);
            verify(websocketSessionManager).getSessionBy(recipientMemberIdB);
            verifyNoInteractions(spaceManager);

            ArgumentCaptor<TextMessage> messageCaptorA = ArgumentCaptor.forClass(TextMessage.class);
            verify(recipientSessionA).sendMessage(messageCaptorA.capture());
            assertThat(messageCaptorA.getValue().getPayload()).isEqualTo("{\"messageType\":\"SPACE_INVITED\"}");

            ArgumentCaptor<TextMessage> messageCaptorB = ArgumentCaptor.forClass(TextMessage.class);
            verify(recipientSessionB).sendMessage(messageCaptorB.capture());
            assertThat(messageCaptorB.getValue().getPayload()).isEqualTo("{\"messageType\":\"SPACE_INVITED\"}");
        }
    }

    @Nested
    @DisplayName("onDiscussionMessagePublished")
    class OnDiscussionMessagePublished {

        @Test
        @DisplayName("onDiscussionMessagePublished는 DISCUSSION_MESSAGE_EVENT를 Space 세션에 전송한다.")
        void onDiscussionMessagePublished는_DISCUSSION_MESSAGE_EVENT를_Space_세션에_전송한다() throws IOException {
            // given
            Long chatRoomId = 9L;

            WebSocketSession roomSession = mock(WebSocketSession.class);
            given(roomSession.isOpen()).willReturn(true);
            given(spaceManager.getWebSocketSessionBy(chatRoomId)).willReturn(Set.of(roomSession));

            DiscussionMessageEvent discussionMessageEvent = DiscussionMessageEvent.builder()
                    .messageType(MessageType.DISCUSSION_MESSAGE_EVENT)
                    .spaceId(chatRoomId)
                    .build();

            given(objectMapper.writeValueAsString(any(DiscussionMessageEvent.class)))
                    .willReturn("{\"messageType\":\"DISCUSSION_MESSAGE_EVENT\"}");

            PublishDiscussionMessageEvent event =
                    new PublishDiscussionMessageEvent(discussionMessageEvent, chatRoomId);

            // when
            messageBroadcastListener.onDiscussionMessagePublished(event);

            // then
            verify(spaceManager).getWebSocketSessionBy(chatRoomId);
            verifyNoInteractions(websocketSessionManager);

            ArgumentCaptor<TextMessage> textMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(roomSession).sendMessage(textMessageCaptor.capture());
            assertThat(textMessageCaptor.getValue().getPayload()).isEqualTo("{\"messageType\":\"DISCUSSION_MESSAGE_EVENT\"}");
        }
    }

    @Nested
    @DisplayName("broadcastReadEventBatch")
    class BroadcastReadEventBatch {

        @Test
        @DisplayName("broadcastReadEventBatch는 READ_EVENT_BATCH를 Space 세션에 전송한다.")
        void broadcastReadEventBatch는_READ_EVENT_BATCH를_Space_세션에_전송한다() throws IOException {
            // given
            Long chatRoomId = 5L;

            WebSocketSession roomSession = mock(WebSocketSession.class);
            given(roomSession.isOpen()).willReturn(true);
            given(spaceManager.getWebSocketSessionBy(chatRoomId)).willReturn(Set.of(roomSession));

            given(objectMapper.writeValueAsString(any(ReadEventBatch.class)))
                    .willReturn("{\"messageType\":\"READ_EVENT_BATCH\"}");

            ReadEventBatchItem item = new ReadEventBatchItem(10L, 100L, 110L);
            ReadEventBatch batch = new ReadEventBatch(chatRoomId, List.of(item));

            // when
            messageBroadcastListener.broadcastReadEventBatch(batch);

            // then
            verify(spaceManager).getWebSocketSessionBy(chatRoomId);
            verifyNoInteractions(websocketSessionManager);

            ArgumentCaptor<TextMessage> textMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(roomSession).sendMessage(textMessageCaptor.capture());
            assertThat(textMessageCaptor.getValue().getPayload()).isEqualTo("{\"messageType\":\"READ_EVENT_BATCH\"}");
        }

        @Test
        @DisplayName("broadcastReadEventBatch는 닫힌 세션을 건너뛰고 열린 세션에만 전송한다.")
        void broadcastReadEventBatch는_닫힌_세션을_건너뛰고_열린_세션에만_전송한다() throws IOException {
            // given
            Long chatRoomId = 6L;

            WebSocketSession closedSession = mock(WebSocketSession.class);
            given(closedSession.isOpen()).willReturn(false);

            WebSocketSession openSession = mock(WebSocketSession.class);
            given(openSession.isOpen()).willReturn(true);

            given(spaceManager.getWebSocketSessionBy(chatRoomId)).willReturn(Set.of(closedSession, openSession));

            given(objectMapper.writeValueAsString(any(ReadEventBatch.class)))
                    .willReturn("{\"messageType\":\"READ_EVENT_BATCH\"}");

            ReadEventBatchItem item = new ReadEventBatchItem(10L, 100L, 110L);
            ReadEventBatch batch = new ReadEventBatch(chatRoomId, List.of(item));

            // when
            assertThatCode(() -> messageBroadcastListener.broadcastReadEventBatch(batch))
                    .doesNotThrowAnyException();

            // then
            verify(closedSession, never()).sendMessage(any(TextMessage.class));

            ArgumentCaptor<TextMessage> textMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(openSession).sendMessage(textMessageCaptor.capture());
            assertThat(textMessageCaptor.getValue().getPayload()).isEqualTo("{\"messageType\":\"READ_EVENT_BATCH\"}");

            verifyNoInteractions(websocketSessionManager);
        }

        @Test
        @DisplayName("broadcastReadEventBatch는 한 세션의 전송이 실패해도 나머지 세션에 계속 전송한다.")
        void broadcastReadEventBatch는_한_세션의_전송이_실패해도_나머지_세션에_계속_전송한다() throws IOException {
            // given
            Long chatRoomId = 7L;

            WebSocketSession failedSession = mock(WebSocketSession.class);
            given(failedSession.isOpen()).willReturn(true);
            willThrow(new IOException("send failed")).given(failedSession).sendMessage(any(TextMessage.class));

            WebSocketSession normalSession = mock(WebSocketSession.class);
            given(normalSession.isOpen()).willReturn(true);

            Set<WebSocketSession> sessions = new LinkedHashSet<>();
            sessions.add(failedSession);
            sessions.add(normalSession);
            given(spaceManager.getWebSocketSessionBy(chatRoomId)).willReturn(sessions);

            given(objectMapper.writeValueAsString(any(ReadEventBatch.class)))
                    .willReturn("{\"messageType\":\"READ_EVENT_BATCH\"}");

            ReadEventBatchItem item = new ReadEventBatchItem(10L, 100L, 110L);
            ReadEventBatch batch = new ReadEventBatch(chatRoomId, List.of(item));

            // when
            assertThatCode(() -> messageBroadcastListener.broadcastReadEventBatch(batch))
                    .doesNotThrowAnyException();

            // then
            verify(failedSession).sendMessage(any(TextMessage.class));

            ArgumentCaptor<TextMessage> textMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(normalSession).sendMessage(textMessageCaptor.capture());
            assertThat(textMessageCaptor.getValue().getPayload()).isEqualTo("{\"messageType\":\"READ_EVENT_BATCH\"}");

            verifyNoInteractions(websocketSessionManager);
        }

        @Test
        @DisplayName("broadcastReadEventBatch는 payload 직렬화가 실패하면 전송하지 않고 예외를 전파하지 않는다.")
        void broadcastReadEventBatch는_payload_직렬화가_실패하면_전송하지_않고_예외를_전파하지_않는다() throws IOException {
            // given
            Long chatRoomId = 8L;

            WebSocketSession session = mock(WebSocketSession.class);
            given(spaceManager.getWebSocketSessionBy(chatRoomId)).willReturn(Set.of(session));

            JsonProcessingException serializationException =
                    new JsonProcessingException("serialization failed") {
                    };
            willThrow(serializationException)
                    .given(objectMapper)
                    .writeValueAsString(any(ReadEventBatch.class));

            ReadEventBatchItem item = new ReadEventBatchItem(10L, 100L, 110L);
            ReadEventBatch batch = new ReadEventBatch(chatRoomId, List.of(item));

            // when
            assertThatCode(() -> messageBroadcastListener.broadcastReadEventBatch(batch))
                    .doesNotThrowAnyException();

            // then
            verify(spaceManager).getWebSocketSessionBy(chatRoomId);
            verify(session, never()).sendMessage(any(TextMessage.class));
            verifyNoInteractions(websocketSessionManager);
        }
    }
}
