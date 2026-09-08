package com.chat.socket.handler;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.DiscussionRepository;
import com.chat.repository.MessageRepository;
import com.chat.service.dtos.chat.EnterRoomRequest;
import com.chat.service.dtos.chat.ReadUpToRequest;
import com.chat.service.dtos.chat.RoomActiveRequest;
import com.chat.service.dtos.chat.RoomInactiveRequest;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.SendDiscussionMessage;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.chat.fixture.ClientMessageIdFixture.nextClientMessageId;
import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTextSocketHandlerTest {

    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private SocketFixture socketFixture;
    @Autowired
    private DiscussionRepository discussionRepository;
    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private WebsocketSessionManager websocketSessionManager;
    @Autowired
    private SpaceManager spaceManager;

    @LocalServerPort
    private int port;

    @BeforeEach
    void beforeTearDown() {
        fixture.deleteAllData();
        websocketSessionManager.clearAll();
        spaceManager.clearAll();
    }

    @AfterEach
    void afterTearDown() {
        fixture.deleteAllData();
        websocketSessionManager.clearAll();
        spaceManager.clearAll();
    }

    private void awaitCondition(String description, BooleanSupplier condition) {
        long timeoutMs = 2000L;
        long pollIntervalMs = 20L;
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(
                        "Interrupted while waiting for condition: " + description, e);
            }
        }

        if (!condition.getAsBoolean()) {
            throw new AssertionError(
                    "Condition not met within " + timeoutMs + "ms: " + description);
        }
    }

    @Test
    @DisplayName("잘못된 형식의 메시지를 전송하면 requestType 없이 INVALID_MESSAGE 에러 응답을 받는다.")
    void 잘못된_형식의_메시지를_전송하면_requestType_없이_INVALID_MESSAGE_에러_응답을_받는다() throws ExecutionException, InterruptedException, IOException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);

        String JSessionId = memberFixture.loginRequestBy(username, port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        WebSocketSession session = socketFixture.connectSocket(
                JSessionId, member.getId(), port, receivedMessages, latch);

        // when: 파싱 자체가 실패하는 payload 전송
        session.sendMessage(new TextMessage("{ not-valid-json"));

        // then: requestType을 알 수 없으므로 null
        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode node = objectMapper.readTree(receivedMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("ERROR");
        assertThat(node.get("errorCode").asText()).isEqualTo("INVALID_MESSAGE");
        assertThat(node.get("requestType").isNull()).isTrue();
        assertThat(node.get("chatRoomId").isNull()).isTrue();
    }

    @Nested
    @DisplayName("Connection / Session Lifecycle")
    class ConnectionLifecycle {

        @Test
        @DisplayName("WebSocket 연결이 종료되면 세션 관리자와 Space에서 세션이 모두 정리된다.")
        void WebSocket_연결_종료_시_세션_관리자와_Space에서_세션이_정리된다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space chatRoom = fixture.savedChatRoomBy("title", participants);
            Long spaceId = chatRoom.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());
            WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
            spaceManager.addSessionToSpace(serverSession, spaceId);

            assertThat(spaceManager.getWebSocketSessionBy(spaceId)).isNotEmpty();
            assertThat(spaceManager.isSpaceActive(memberId, spaceId)).isTrue();

            // when: 세션 종료
            session.close(CloseStatus.NORMAL);

            awaitCondition(
                    "WebSocket session cleanup after close",
                    () -> websocketSessionManager.getSessionBy(memberId).isEmpty());

            assertThat(websocketSessionManager.getSessionBy(memberId)).isEmpty();
            assertThat(spaceManager.getWebSocketSessionBy(spaceId)).isEmpty();
            assertThat(spaceManager.isSpaceActive(memberId, spaceId)).isFalse();
        }

        @Test
        @DisplayName("동일 memberId의 WebSocket Session 2개 중 하나만 종료하면 다른 Session은 유지된다.")
        void 동일_memberId의_Session_2개_중_하나만_종료하면_다른_Session은_유지된다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latchA = new CountDownLatch(1);
            List<String> receivedMessagesA = new ArrayList<>();

            CountDownLatch latchB = new CountDownLatch(1);
            List<String> receivedMessagesB = new ArrayList<>();

            WebSocketSession sessionA = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessagesA, latchA);
            WebSocketSession sessionB = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessagesB, latchB);

            try {
                awaitCondition(
                        "two WebSocket sessions registration",
                        () -> websocketSessionManager.getSessionBy(memberId).size() == 2);
                assertThat(websocketSessionManager.getSessionBy(memberId)).hasSize(2);

                // when
                sessionA.close(CloseStatus.NORMAL);

                awaitCondition(
                        "one WebSocket session remaining after close",
                        () -> websocketSessionManager.getSessionBy(memberId).size() == 1);

                // then
                assertThat(websocketSessionManager.getSessionBy(memberId)).hasSize(1);
            } finally {
                if (sessionB.isOpen()) {
                    sessionB.close(CloseStatus.NORMAL);
                }
            }
        }
    }

    @Nested
    @DisplayName("Room Lifecycle")
    class RoomLifecycle {

        @Test
        @DisplayName("Space 참여자가 ENTER_ROOM을 전송하면 세션이 Space에 등록되고 ENTER_ROOM_ACK을 수신한다.")
        void 참여자가_ENTER_ROOM을_전송하면_Space에_등록되고_ENTER_ROOM_ACK을_수신한다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space space = fixture.savedChatRoomBy("title", participants);
            Long spaceId = space.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());

            // when
            ObjectMapper objectMapper = new ObjectMapper();
            EnterRoomRequest enterRoom = EnterRoomRequest.builder()
                    .messageType(MessageType.ENTER_ROOM)
                    .chatRoomId(spaceId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(enterRoom)));

            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            assertThat(spaceManager.getWebSocketSessionBy(spaceId)).isNotEmpty();
            assertThat(spaceManager.isSpaceActive(memberId, spaceId)).isTrue();

            JsonNode node = objectMapper.readTree(receivedMessages.get(0));
            assertThat(node.get("messageType").asText()).isEqualTo("ENTER_ROOM_ACK");
            assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
        }

        @Test
        @DisplayName("room1에 ENTER_ROOM한 세션이 room2로 다시 ENTER_ROOM하면 이전 Space에서 빠지고 새 Space로 전환된다.")
        void ENTER_ROOM으로_room1에_참여한_세션이_room2로_ENTER_ROOM하면_Space가_전환된다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space room1 = fixture.savedChatRoomBy("room1", participants);
            Space room2 = fixture.savedChatRoomBy("room2", participants);
            Long room1Id = room1.getId();
            Long room2Id = room2.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            // room1, room2 ENTER_ROOM_ACK을 모두 수신하기 위한 latch
            CountDownLatch latch = new CountDownLatch(2);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());

            ObjectMapper objectMapper = new ObjectMapper();
            EnterRoomRequest enterRoom1 = EnterRoomRequest.builder()
                    .messageType(MessageType.ENTER_ROOM)
                    .chatRoomId(room1Id)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(enterRoom1)));

            // room1 ENTER_ROOM 처리 완료 대기
            awaitCondition(
                    "first ENTER_ROOM activation",
                    () -> spaceManager.isSpaceActive(memberId, room1Id));

            // 전환 전 baseline: ENTER_ROOM 성공 자체의 재검증이 아니라 room1 -> room2 전환 전후를 대조하기 위한 계측점
            assertThat(spaceManager.getWebSocketSessionBy(room1Id)).isNotEmpty();
            assertThat(spaceManager.isSpaceActive(memberId, room1Id)).isTrue();

            // when
            EnterRoomRequest enterRoom2 = EnterRoomRequest.builder()
                    .messageType(MessageType.ENTER_ROOM)
                    .chatRoomId(room2Id)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(enterRoom2)));

            // then: 두 ENTER_ROOM_ACK이 모두 수신될 때까지 대기하는 것이 최종 결정적 동기화 지점
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            assertThat(spaceManager.getWebSocketSessionBy(room1Id)).isEmpty();
            assertThat(spaceManager.isSpaceActive(memberId, room1Id)).isFalse();
            assertThat(spaceManager.isSpaceActive(memberId, room2Id)).isTrue();

            // 두 번째 ENTER_ROOM_ACK이 실제 room2 전환에 대한 응답인지 확인 (latch 동기화 지점이 맞았다는 증거)
            JsonNode secondAckNode = objectMapper.readTree(receivedMessages.get(1));
            assertThat(secondAckNode.get("messageType").asText()).isEqualTo("ENTER_ROOM_ACK");
            assertThat(secondAckNode.get("chatRoomId").asLong()).isEqualTo(room2Id);
        }

        @Test
        @DisplayName("ENTER_ROOM으로 active 상태인 세션이 ROOM_INACTIVE를 전송하면 Space가 inactive 상태가 된다.")
        void ENTER_ROOM으로_active_상태인_세션이_ROOM_INACTIVE를_전송하면_Space가_inactive_상태가_된다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space space = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = space.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());

            ObjectMapper objectMapper = new ObjectMapper();
            EnterRoomRequest enterRoom = EnterRoomRequest.builder()
                    .messageType(MessageType.ENTER_ROOM)
                    .chatRoomId(chatRoomId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(enterRoom)));

            boolean receivedAck = latch.await(2, TimeUnit.SECONDS);
            assertThat(receivedAck).isTrue();
            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isTrue();

            // when
            RoomInactiveRequest roomInactive = RoomInactiveRequest.builder()
                    .messageType(MessageType.ROOM_INACTIVE)
                    .chatRoomId(chatRoomId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(roomInactive)));

            // ROOM_INACTIVE는 클라이언트로 응답을 보내지 않으므로 상태가 반영될 때까지 폴링한다
            awaitCondition(
                    "Space inactive state",
                    () -> !spaceManager.isSpaceActive(memberId, chatRoomId));

            // then
            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isFalse();
        }

        @Test
        @DisplayName("ROOM_INACTIVE로 inactive 상태인 세션이 ROOM_ACTIVE를 전송하면 Space가 다시 active 상태가 된다.")
        void ROOM_INACTIVE로_inactive_상태인_세션이_ROOM_ACTIVE를_전송하면_Space가_다시_active_상태가_된다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space space = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = space.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());

            ObjectMapper objectMapper = new ObjectMapper();
            EnterRoomRequest enterRoom = EnterRoomRequest.builder()
                    .messageType(MessageType.ENTER_ROOM)
                    .chatRoomId(chatRoomId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(enterRoom)));

            boolean receivedAck = latch.await(2, TimeUnit.SECONDS);
            assertThat(receivedAck).isTrue();

            RoomInactiveRequest roomInactive = RoomInactiveRequest.builder()
                    .messageType(MessageType.ROOM_INACTIVE)
                    .chatRoomId(chatRoomId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(roomInactive)));

            // ROOM_INACTIVE는 클라이언트로 응답을 보내지 않으므로 상태가 반영될 때까지 폴링한다
            awaitCondition(
                    "Space inactive state",
                    () -> !spaceManager.isSpaceActive(memberId, chatRoomId));
            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isFalse();

            // when
            RoomActiveRequest roomActive = RoomActiveRequest.builder()
                    .messageType(MessageType.ROOM_ACTIVE)
                    .chatRoomId(chatRoomId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(roomActive)));

            awaitCondition(
                    "Space active state",
                    () -> spaceManager.isSpaceActive(memberId, chatRoomId));

            // then
            assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isTrue();
        }

        @Test
        @DisplayName("읽지 않은 메시지가 있는 세션이 ROOM_ACTIVE를 전송하면 READ_EVENT_BATCH를 수신한다.")
        void ROOM_ACTIVE를_전송하면_읽지_않은_최신_메시지까지_읽음_처리되어_READ_EVENT_BATCH를_수신한다() throws ExecutionException, InterruptedException, IOException {
            // given
            String senderUsername = "sender";
            String receiverUsername = "receiver";
            Member sender = memberFixture.saveEncryptPasswordBy(senderUsername);
            Member receiver = memberFixture.saveEncryptPasswordBy(receiverUsername);
            Long receiverId = receiver.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(sender);
            participants.add(receiver);
            Space space = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = space.getId();

            // CHAT_MESSAGE 라우팅/sender cursor 부작용 없이 unread 메시지만 최소 구성
            fixture.savedSimpleChat("hello", sender, space);

            String JSessionId = memberFixture.loginRequestBy(receiverUsername, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, receiverId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(receiverId).isEmpty());
            WebSocketSession serverSession = websocketSessionManager.getSessionBy(receiverId).iterator().next();
            spaceManager.addSessionToSpace(serverSession, chatRoomId);

            ObjectMapper objectMapper = new ObjectMapper();
            RoomActiveRequest roomActive = RoomActiveRequest.builder()
                    .messageType(MessageType.ROOM_ACTIVE)
                    .chatRoomId(chatRoomId)
                    .build();

            // when
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(roomActive)));

            // then: onRoomActive의 read publish 파이프라인을 거쳐 READ_EVENT_BATCH가 도착한다
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            JsonNode readEventBatchNode = objectMapper.readTree(receivedMessages.get(0));
            assertThat(readEventBatchNode.get("messageType").asText()).isEqualTo("READ_EVENT_BATCH");

            List<Long> readMemberIds = new ArrayList<>();
            for (JsonNode readItemNode : readEventBatchNode.get("reads")) {
                readMemberIds.add(readItemNode.get("memberId").asLong());
            }
            assertThat(readMemberIds).containsExactly(receiverId);
        }

        @Test
        @DisplayName("Space 비참여자가 ENTER_ROOM을 전송하면 ROOM_NOT_FOUND 에러 응답을 requestType/chatRoomId와 함께 받고 Space에 등록되지 않는다.")
        void 비참여자가_ENTER_ROOM을_전송하면_ROOM_NOT_FOUND_에러_응답을_requestType_chatRoomId와_함께_받고_Space에_등록되지_않는다() throws ExecutionException, InterruptedException, IOException {
            // given
            Member owner = memberFixture.saveEncryptPasswordBy("owner");
            Member intruder = memberFixture.saveEncryptPasswordBy("intruder");

            List<Member> participants = new ArrayList<>();
            participants.add(owner);
            Space space = fixture.savedChatRoomBy("title", participants);
            Long spaceId = space.getId();

            String intruderJSessionId = memberFixture.loginRequestBy("intruder", port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    intruderJSessionId, intruder.getId(), port, receivedMessages, latch);

            // when
            ObjectMapper objectMapper = new ObjectMapper();
            EnterRoomRequest enterRoom = EnterRoomRequest.builder()
                    .messageType(MessageType.ENTER_ROOM)
                    .chatRoomId(spaceId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(enterRoom)));

            // then: validateParticipant → CustomException(SPACE_NOT_FOUND) → mapErrorCode → ROOM_NOT_FOUND
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            JsonNode node = objectMapper.readTree(receivedMessages.get(0));
            assertThat(node.get("messageType").asText()).isEqualTo("ERROR");
            assertThat(node.get("errorCode").asText()).isEqualTo("ROOM_NOT_FOUND");
            assertThat(node.get("requestType").asText()).isEqualTo("ENTER_ROOM");
            assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
            assertThat(spaceManager.getWebSocketSessionBy(spaceId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Chat Message")
    class ChatMessage {

        @Test
        @DisplayName("Space에 등록된 세션이 CHAT_MESSAGE를 전송하면 브로드캐스트된다.")
        void Space에_등록된_세션이_CHAT_MESSAGE를_전송하면_브로드캐스트된다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space chatRoom = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = chatRoom.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            // CHAT_MESSAGE + ROOM_MESSAGE_SUMMARY_UPDATED + (sender 본인 cursor 전진에 따른) READ_EVENT_BATCH = 3개
            CountDownLatch latch = new CountDownLatch(3);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());
            WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
            spaceManager.addSessionToSpace(serverSession, chatRoomId);

            ObjectMapper objectMapper = new ObjectMapper();
            String clientMessageId = nextClientMessageId();
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(chatRoomId)
                    .message("message")
                    .clientMessageId(clientMessageId)
                    .build();
            String chat = objectMapper.writeValueAsString(sendChat);

            // when
            session.sendMessage(new TextMessage(chat));

            // then: CHAT_MESSAGE + ROOM_MESSAGE_SUMMARY_UPDATED는 즉시, READ_EVENT_BATCH는 배치 스케줄러(100ms)를 통해 도착한다
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();
            assertThat(receivedMessages).hasSize(3);

            List<JsonNode> receivedNodes = receivedMessages.stream()
                    .map(message -> {
                        try {
                            return objectMapper.readTree(message);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            List<String> messageTypes = receivedNodes.stream()
                    .map(node -> node.get("messageType").asText())
                    .toList();
            assertThat(messageTypes).containsExactlyInAnyOrder(
                    "CHAT_MESSAGE", "ROOM_MESSAGE_SUMMARY_UPDATED", "READ_EVENT_BATCH");

            JsonNode chatMessage = receivedNodes.stream()
                    .filter(node -> "CHAT_MESSAGE".equals(node.get("messageType").asText()))
                    .findFirst()
                    .orElseThrow();

            assertThat(chatMessage.get("clientMessageId").asText())
                    .isEqualTo(clientMessageId);
        }

        @Test
        @DisplayName("Space에 등록되지 않은 세션이 CHAT_MESSAGE를 전송하면 ROOM_NOT_JOINED 에러 응답을 clientMessageId와 함께 받는다.")
        void Space에_등록되지_않은_세션이_CHAT_MESSAGE를_전송하면_ROOM_NOT_JOINED_에러_응답을_받는다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space chatRoom = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = chatRoom.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            // ENTER_ROOM을 보내지 않아 chatRoomManager에 세션이 없는 상태

            ObjectMapper objectMapper = new ObjectMapper();
            String clientMessageId = nextClientMessageId();
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(chatRoomId)
                    .message("blocked message")
                    .clientMessageId(clientMessageId)
                    .build();

            // when: 세션이 방에 없는 상태에서 CHAT_MESSAGE 전송
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sendChat)));

            // then: silent drop 대신 ROOM_NOT_JOINED 에러 응답을 clientMessageId와 함께 수신해야 함
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            JsonNode node = objectMapper.readTree(receivedMessages.get(0));
            assertThat(node.get("messageType").asText()).isEqualTo("ERROR");
            assertThat(node.get("errorCode").asText()).isEqualTo("ROOM_NOT_JOINED");
            assertThat(node.get("requestType").asText()).isEqualTo("CHAT_MESSAGE");
            assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
            assertThat(node.get("clientMessageId").asText()).isEqualTo(clientMessageId);
            assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).isEmpty();
        }

        @Test
        @DisplayName("clientMessageId가 누락된 CHAT_MESSAGE를 전송하면 INVALID_MESSAGE 에러 응답을 받는다.")
        void clientMessageId가_누락된_CHAT_MESSAGE는_INVALID_MESSAGE_오류_응답을_받는다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space chatRoom = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = chatRoom.getId();

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());
            WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
            spaceManager.addSessionToSpace(serverSession, chatRoomId);

            ObjectMapper objectMapper = new ObjectMapper();
            // clientMessageId를 의도적으로 생략 — 이 테스트의 조건 자체이므로 nextClientMessageId()를 호출하지 않는다
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(chatRoomId)
                    .message("message")
                    .build();

            // when: clientMessageId 프로퍼티 자체가 없는 JSON을 전송
            ObjectNode requestNode = objectMapper.valueToTree(sendChat);
            requestNode.remove("clientMessageId");
            String chat = objectMapper.writeValueAsString(requestNode);
            session.sendMessage(new TextMessage(chat));

            // then: MessageService.validateClientMessageId()가 Member/Space 조회, Message 저장, broadcast보다 먼저
            // 예외를 던지므로 CHAT_MESSAGE/ROOM_MESSAGE_SUMMARY_UPDATED/READ_EVENT_BATCH는 발행될 수 없고
            // ERROR 응답 1건만 수신된다
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();
            assertThat(receivedMessages).hasSize(1);

            JsonNode node = objectMapper.readTree(receivedMessages.get(0));
            assertThat(node.get("messageType").asText()).isEqualTo("ERROR");
            assertThat(node.get("requestType").asText()).isEqualTo("CHAT_MESSAGE");
            assertThat(node.get("errorCode").asText()).isEqualTo("INVALID_MESSAGE");
            assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
            assertThat(node.get("clientMessageId").isNull()).isTrue();
        }

        @Test
        @DisplayName("다른 sender가 같은 clientMessageId를 사용하면 CLIENT_MESSAGE_ID_CONFLICT 에러 응답을 받는다.")
        void 다른_sender가_같은_clientMessageId를_사용하면_CLIENT_MESSAGE_ID_CONFLICT_오류_응답을_받는다() throws ExecutionException, InterruptedException, IOException {
            // given
            String usernameA = "senderA";
            String usernameB = "senderB";
            Member memberA = memberFixture.saveEncryptPasswordBy(usernameA);
            Member memberB = memberFixture.saveEncryptPasswordBy(usernameB);

            List<Member> participants = new ArrayList<>();
            participants.add(memberA);
            participants.add(memberB);
            Space chatRoom = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = chatRoom.getId();

            String message = "message";
            String clientMessageId = nextClientMessageId();
            // A 소유의 기존 Message를 미리 준비 (요청과 동일한 room/content, sender만 다름)
            messageRepository.save(Message.of(message, memberA, chatRoom, clientMessageId));

            String JSessionId = memberFixture.loginRequestBy(usernameB, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberB.getId(), port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberB.getId()).isEmpty());
            WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberB.getId()).iterator().next();
            spaceManager.addSessionToSpace(serverSession, chatRoomId);

            ObjectMapper objectMapper = new ObjectMapper();
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(chatRoomId)
                    .message(message)
                    .clientMessageId(clientMessageId)
                    .build();
            String json = objectMapper.writeValueAsString(sendChat);

            // when: B가 A 소유의 기존 clientMessageId로 같은 room/content를 재요청
            session.sendMessage(new TextMessage(json));

            // then: MessageService의 sender 불일치 판정이 CLIENT_MESSAGE_ID_CONFLICT로 매핑되어 응답된다
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();
            assertThat(receivedMessages).hasSize(1);

            JsonNode node = objectMapper.readTree(receivedMessages.get(0));
            assertThat(node.get("messageType").asText()).isEqualTo("ERROR");
            assertThat(node.get("requestType").asText()).isEqualTo("CHAT_MESSAGE");
            assertThat(node.get("errorCode").asText()).isEqualTo("CLIENT_MESSAGE_ID_CONFLICT");
            assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
            assertThat(node.get("clientMessageId").asText()).isEqualTo(clientMessageId);
        }
    }

    @Nested
    @DisplayName("Read")
    class Read {

        @Test
        @DisplayName("참여 세션이 READ_UP_TO를 전송하면 READ_EVENT_BATCH만 수신하고 다른 이벤트는 수신하지 않는다.")
        void 참여_세션이_READ_UP_TO를_전송하면_READ_EVENT_BATCH만_수신한다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space chatRoom = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = chatRoom.getId();

            Message chat = fixture.savedSimpleChat("hello", member, chatRoom);

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());
            WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
            spaceManager.addSessionToSpace(serverSession, chatRoomId);

            ObjectMapper objectMapper = new ObjectMapper();
            ReadUpToRequest readUpTo = ReadUpToRequest.builder()
                    .messageType(MessageType.READ_UP_TO)
                    .chatRoomId(chatRoomId)
                    .lastReadMessageId(chat.getId())
                    .build();

            // when
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(readUpTo)));

            // then: READ_EVENT_BATCH(room 세션 broadcast)만 발생
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            // 다른 이벤트가 뒤늦게라도 오지 않는지 짧게 추가 대기 후 재확인
            long SERVER_SESSION_REGISTER_WAIT_MS = 300;
            Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
            assertThat(receivedMessages).hasSize(1);

            JsonNode readEventBatchNode = objectMapper.readTree(receivedMessages.get(0));
            assertThat(readEventBatchNode.get("messageType").asText()).isEqualTo("READ_EVENT_BATCH");

            JsonNode readItemNode = readEventBatchNode.get("reads").get(0);
            assertThat(readItemNode.get("memberId").asLong()).isEqualTo(memberId);
        }

        @Test
        @DisplayName("같은 Space의 두 세션이 연결된 상태에서 한 사용자가 READ_UP_TO를 전송하면 두 세션 모두 READ_EVENT_BATCH를 수신한다.")
        void 같은_Space의_두_세션이_연결된_상태에서_한_사용자가_READ_UP_TO를_전송하면_두_세션_모두_READ_EVENT_BATCH를_수신한다() throws ExecutionException, InterruptedException, IOException {
            // given
            String readerUsername = "reader";
            String otherUsername = "other";
            Member reader = memberFixture.saveEncryptPasswordBy(readerUsername);
            Member other = memberFixture.saveEncryptPasswordBy(otherUsername);

            Space space = fixture.savedChatRoomBy("title", List.of(reader, other));
            Long chatRoomId = space.getId();

            // CHAT_MESSAGE 라우팅/sender cursor 부작용 없이 unread 메시지만 최소 구성
            Message message = fixture.savedSimpleChat("hello", other, space);
            Long messageId = message.getId();

            String readerJSessionId = memberFixture.loginRequestBy(readerUsername, port);
            String otherJSessionId = memberFixture.loginRequestBy(otherUsername, port);

            CountDownLatch readerLatch = new CountDownLatch(1);
            List<String> readerMessages = new ArrayList<>();
            WebSocketSession readerSession = socketFixture.connectSocket(
                    readerJSessionId, reader.getId(), port, readerMessages, readerLatch);

            CountDownLatch otherLatch = new CountDownLatch(1);
            List<String> otherMessages = new ArrayList<>();
            socketFixture.connectSocket(
                    otherJSessionId, other.getId(), port, otherMessages, otherLatch);

            awaitCondition(
                    "two WebSocket sessions registration",
                    () -> websocketSessionManager.getSessionBy(reader.getId()).size() == 1
                            && websocketSessionManager.getSessionBy(other.getId()).size() == 1);

            WebSocketSession readerServerSession = websocketSessionManager.getSessionBy(reader.getId()).iterator().next();
            spaceManager.addSessionToSpace(readerServerSession, chatRoomId);
            WebSocketSession otherServerSession = websocketSessionManager.getSessionBy(other.getId()).iterator().next();
            spaceManager.addSessionToSpace(otherServerSession, chatRoomId);

            ObjectMapper objectMapper = new ObjectMapper();
            ReadUpToRequest readUpTo = ReadUpToRequest.builder()
                    .messageType(MessageType.READ_UP_TO)
                    .chatRoomId(chatRoomId)
                    .lastReadMessageId(messageId)
                    .build();

            // when: reader만 READ_UP_TO를 전송
            readerSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(readUpTo)));

            // then: reader/other 두 실제 세션 모두 READ_EVENT_BATCH를 수신한다
            boolean readerReceived = readerLatch.await(2, TimeUnit.SECONDS);
            boolean otherReceived = otherLatch.await(2, TimeUnit.SECONDS);
            assertThat(readerReceived).isTrue();
            assertThat(otherReceived).isTrue();

            JsonNode readerBatchNode = objectMapper.readTree(readerMessages.get(0));
            assertThat(readerBatchNode.get("messageType").asText()).isEqualTo("READ_EVENT_BATCH");
            List<Long> readerBatchMemberIds = new ArrayList<>();
            for (JsonNode readItemNode : readerBatchNode.get("reads")) {
                readerBatchMemberIds.add(readItemNode.get("memberId").asLong());
            }
            assertThat(readerBatchMemberIds).containsExactly(reader.getId());

            JsonNode otherBatchNode = objectMapper.readTree(otherMessages.get(0));
            assertThat(otherBatchNode.get("messageType").asText()).isEqualTo("READ_EVENT_BATCH");
            List<Long> otherBatchMemberIds = new ArrayList<>();
            for (JsonNode readItemNode : otherBatchNode.get("reads")) {
                otherBatchMemberIds.add(readItemNode.get("memberId").asLong());
            }
            assertThat(otherBatchMemberIds).containsExactly(reader.getId());
        }

        @Test
        @DisplayName("Space에 등록되지 않은 세션이 READ_UP_TO를 전송하면 ROOM_NOT_JOINED 에러 응답을 받는다.")
        void Space에_등록되지_않은_세션이_READ_UP_TO를_전송하면_ROOM_NOT_JOINED_에러_응답을_받는다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space chatRoom = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = chatRoom.getId();

            Message chat = fixture.savedSimpleChat("hello", member, chatRoom);

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            // ENTER_ROOM을 보내지 않아 세션이 room에 등록되지 않은 상태

            ObjectMapper objectMapper = new ObjectMapper();
            ReadUpToRequest readUpTo = ReadUpToRequest.builder()
                    .messageType(MessageType.READ_UP_TO)
                    .chatRoomId(chatRoomId)
                    .lastReadMessageId(chat.getId())
                    .build();

            // when
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(readUpTo)));

            // then
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            JsonNode node = objectMapper.readTree(receivedMessages.get(0));
            assertThat(node.get("messageType").asText()).isEqualTo("ERROR");
            assertThat(node.get("errorCode").asText()).isEqualTo("ROOM_NOT_JOINED");
            assertThat(node.get("requestType").asText()).isEqualTo("READ_UP_TO");
            assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
        }

        @Test
        @DisplayName("다른 room의 messageId로 READ_UP_TO를 전송하면 MESSAGE_NOT_FOUND 에러 응답을 받는다.")
        void 다른_room의_messageId로_READ_UP_TO를_전송하면_MESSAGE_NOT_FOUND_에러_응답을_받는다() throws ExecutionException, InterruptedException, IOException {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);
            Long memberId = member.getId();

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space chatRoom = fixture.savedChatRoomBy("title", participants);
            Long chatRoomId = chatRoom.getId();

            Space otherRoom = fixture.savedChatRoomBy("otherRoom", participants);
            Message otherRoomChat = fixture.savedSimpleChat("hello", member, otherRoom);

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession session = socketFixture.connectSocket(
                    JSessionId, memberId, port, receivedMessages, latch);

            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(memberId).isEmpty());
            WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
            spaceManager.addSessionToSpace(serverSession, chatRoomId);

            ObjectMapper objectMapper = new ObjectMapper();
            ReadUpToRequest readUpTo = ReadUpToRequest.builder()
                    .messageType(MessageType.READ_UP_TO)
                    .chatRoomId(chatRoomId)
                    .lastReadMessageId(otherRoomChat.getId())
                    .build();

            // when
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(readUpTo)));

            // then
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            JsonNode node = objectMapper.readTree(receivedMessages.get(0));
            assertThat(node.get("messageType").asText()).isEqualTo("ERROR");
            assertThat(node.get("errorCode").asText()).isEqualTo("MESSAGE_NOT_FOUND");
            assertThat(node.get("requestType").asText()).isEqualTo("READ_UP_TO");
            assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
        }
    }

    @Nested
    @DisplayName("Discussion")
    class Discussion {

        @Test
        @DisplayName("DISCUSSION_MESSAGE 전송 시 Space 세션에 DISCUSSION_MESSAGE_EVENT가 수신된다.")
        void DISCUSSION_MESSAGE_전송_시_Space_세션에_DISCUSSION_MESSAGE_EVENT가_수신된다()
                throws Exception {
            // given
            String username = "username";
            Member member = memberFixture.saveEncryptPasswordBy(username);

            List<Member> participants = new ArrayList<>();
            participants.add(member);
            Space space = fixture.savedChatRoomBy("space", participants);
            Message rootMessage = fixture.savedSimpleChat("원글", member, space);
            com.chat.entity.Discussion discussion = discussionRepository.save(com.chat.entity.Discussion.of(rootMessage));

            String JSessionId = memberFixture.loginRequestBy(username, port);

            CountDownLatch latch = new CountDownLatch(1);
            List<String> receivedMessages = new ArrayList<>();
            WebSocketSession clientSession = socketFixture.connectSocket(
                    JSessionId, member.getId(), port, receivedMessages, latch);

            // 서버 세션을 Space에 등록
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(member.getId()).isEmpty());
            WebSocketSession serverSession =
                    websocketSessionManager.getSessionBy(member.getId()).iterator().next();
            spaceManager.addSessionToSpace(serverSession, space.getId());

            ObjectMapper objectMapper = new ObjectMapper();
            SendDiscussionMessage sendDiscussionMessage = SendDiscussionMessage.builder()
                    .messageType(MessageType.DISCUSSION_MESSAGE)
                    .discussionId(discussion.getId())
                    .content("핸들러 테스트 답글")
                    .build();

            // when
            clientSession.sendMessage(
                    new TextMessage(objectMapper.writeValueAsString(sendDiscussionMessage)));

            // then: DISCUSSION_MESSAGE_EVENT 1건 수신
            boolean received = latch.await(3, TimeUnit.SECONDS);
            assertThat(received).isTrue();
            assertThat(receivedMessages).hasSize(1);

            JsonNode node = objectMapper.readTree(receivedMessages.get(0));
            assertThat(node.get("messageType").asText()).isEqualTo("DISCUSSION_MESSAGE_EVENT");
        }
    }
}
