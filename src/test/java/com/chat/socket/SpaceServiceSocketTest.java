package com.chat.socket;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.MessageRepository;
import com.chat.service.SpaceService;
import com.chat.service.dtos.chat.SendChat;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SpaceServiceSocketTest {

    private static final long SERVER_SESSION_REGISTER_WAIT_MS = 300;
    private static final long BROADCAST_TIMEOUT_SECONDS = 3;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private SocketFixture socketFixture;

    @Autowired
    private SpaceManager spaceManager;
    @Autowired
    private WebsocketSessionManager websocketSessionManager;
    @Autowired
    private MessageRepository messageRepository;

    @LocalServerPort
    private int port;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        fixture.deleteAllData();
        spaceManager.clearAll();
        websocketSessionManager.clearAll();
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

    @Nested
    @DisplayName("broadCastMessage")
    class BroadCastMessage {

        @Test
        @DisplayName("같은 room에 등록된 sender와 receiver 모두 동일한 CHAT_MESSAGE를 수신한다.")
        void 같은_room에_등록된_sender와_receiver_모두_동일한_CHAT_MESSAGE를_수신한다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            Space space = fixture.savedChatRoomBy("title", List.of(first, second));
            Long spaceId = space.getId();

            List<String> firstMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

            List<String> secondMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty()
                            && !websocketSessionManager.getSessionBy(secondId).isEmpty());

            WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
            spaceManager.addSessionToSpace(firstServerSession, spaceId);
            WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
            spaceManager.addSessionToSpace(secondServerSession, spaceId);

            String message = "message";
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(spaceId)
                    .message(message)
                    .build();

            // when
            spaceService.broadCastMessage(firstId, sendChat);

            // then: room에 등록된 sender/receiver 세션 모두 CHAT_MESSAGE를 수신할 때까지 대기
            awaitCondition(
                    "CHAT_MESSAGE received by both sender and receiver",
                    () -> containsMessageType(firstMessages, "CHAT_MESSAGE")
                            && containsMessageType(secondMessages, "CHAT_MESSAGE"));

            JsonNode firstChatMessage = findMessageType(firstMessages, "CHAT_MESSAGE");
            JsonNode secondChatMessage = findMessageType(secondMessages, "CHAT_MESSAGE");

            assertThat(firstChatMessage.get("chatId").asLong()).isEqualTo(secondChatMessage.get("chatId").asLong());
            assertThat(firstChatMessage.get("message").asText()).isEqualTo(message);
            assertThat(secondChatMessage.get("message").asText()).isEqualTo(message);
        }

        @Test
        @DisplayName("clientMessageId를 포함해 메시지를 전송하면 저장 및 CHAT_MESSAGE echo payload에 그대로 포함된다.")
        void clientMessageId를_포함해_메시지를_전송하면_저장_및_echo_payload에_포함된다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            Space space = fixture.savedChatRoomBy("title", List.of(first, second));
            Long spaceId = space.getId();

            List<String> firstMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

            List<String> secondMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty()
                            && !websocketSessionManager.getSessionBy(secondId).isEmpty());

            WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
            spaceManager.addSessionToSpace(firstServerSession, spaceId);
            WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
            spaceManager.addSessionToSpace(secondServerSession, spaceId);

            String message = "message";
            String clientMessageId = "client-uuid-5678";
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(spaceId)
                    .message(message)
                    .clientMessageId(clientMessageId)
                    .build();

            // when
            spaceService.broadCastMessage(firstId, sendChat);

            // then
            awaitCondition(
                    "CHAT_MESSAGE received by receiver",
                    () -> containsMessageType(secondMessages, "CHAT_MESSAGE"));

            JsonNode chatMessage = findMessageType(secondMessages, "CHAT_MESSAGE");
            assertThat(chatMessage.get("clientMessageId").asText()).isEqualTo(clientMessageId);

            Long messageId = chatMessage.get("chatId").asLong();
            Message foundMessage = messageRepository.findById(messageId).orElseThrow();
            assertThat(foundMessage.getClientMessageId()).isEqualTo(clientMessageId);
        }

        @Test
        @DisplayName("CHAT_MESSAGE의 senderId와 senderNickname은 broadCastMessage 호출값과 발신자 DB 정보를 그대로 반영한다.")
        void 메시지_전송_시_senderId와_senderNickname은_호출값과_발신자_정보를_반영한다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            Space space = fixture.savedChatRoomBy("title", List.of(first, second));
            Long spaceId = space.getId();

            List<String> firstMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

            List<String> secondMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty()
                            && !websocketSessionManager.getSessionBy(secondId).isEmpty());

            WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
            spaceManager.addSessionToSpace(firstServerSession, spaceId);
            WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
            spaceManager.addSessionToSpace(secondServerSession, spaceId);

            // SendChat에 chatRoomId, message만 포함 — senderId, senderNickname 없음
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(spaceId)
                    .message("hello")
                    .build();

            // when: memberId = firstId
            spaceService.broadCastMessage(firstId, sendChat);

            // then
            awaitCondition(
                    "CHAT_MESSAGE received by receiver",
                    () -> containsMessageType(secondMessages, "CHAT_MESSAGE"));

            JsonNode chatMessage = findMessageType(secondMessages, "CHAT_MESSAGE");
            assertThat(chatMessage.get("senderId").asLong()).isEqualTo(firstId);
            assertThat(chatMessage.get("senderNickname").asText()).isEqualTo(first.getNickname());
        }

        @Test
        @DisplayName("같은 clientMessageId로 두 번 전송하면 중복 저장 없이 동일 Message 정보로 CHAT_MESSAGE가 두 번 응답된다.")
        void 같은_clientMessageId로_두_번_전송하면_중복_저장_없이_동일_Message_정보로_두_번_응답된다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            Space space = fixture.savedChatRoomBy("title", List.of(first, second));
            Long spaceId = space.getId();

            List<String> firstMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty());

            WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
            spaceManager.addSessionToSpace(firstServerSession, spaceId);

            String message = "retry message";
            String clientMessageId = "client-uuid-retry-broadcast";
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(spaceId)
                    .message(message)
                    .clientMessageId(clientMessageId)
                    .build();

            // when: 같은 clientMessageId로 두 번 전송 (재전송 시나리오, saveMessageEntity가 기존 Message를 그대로 반환하는 경로)
            spaceService.broadCastMessage(firstId, sendChat);
            spaceService.broadCastMessage(firstId, sendChat);

            // then: DB에는 하나의 Message만 저장된다 (중복 INSERT 없음)
            long savedCount = messageRepository.findAll().stream()
                    .filter(m -> clientMessageId.equals(m.getClientMessageId()))
                    .count();
            assertThat(savedCount).isEqualTo(1L);

            Message savedMessage = messageRepository.findAll().stream()
                    .filter(m -> clientMessageId.equals(m.getClientMessageId()))
                    .findFirst()
                    .orElseThrow();

            // CHAT_MESSAGE 2건이 모두 도착할 때까지 대기한다.
            // ROOM_MESSAGE_SUMMARY_UPDATED 등 다른 이벤트 개수와는 결합하지 않는다 (다른 테스트가 이미 보호하는 계약).
            awaitCondition(
                    "두 번의 broadCastMessage 호출에 대응하는 CHAT_MESSAGE 2건 수신",
                    () -> countMessageType(firstMessages, "CHAT_MESSAGE") == 2);

            List<JsonNode> chatMessages = firstMessages.stream()
                    .map(SpaceServiceSocketTest.this::readTree)
                    .filter(node -> "CHAT_MESSAGE".equals(node.get("messageType").asText()))
                    .collect(Collectors.toList());
            assertThat(chatMessages).hasSize(2);

            for (JsonNode node : chatMessages) {
                assertThat(node.get("chatId").asLong()).isEqualTo(savedMessage.getId());
                assertThat(node.get("clientMessageId").asText()).isEqualTo(clientMessageId);
            }
        }

        @Test
        @DisplayName("메시지 전송 시 room(spaceManager) 등록 여부와 무관하게 참여자 세션에 ROOM_MESSAGE_SUMMARY_UPDATED가 전송되고 저장된 메시지 정보와 일치한다.")
        void 메시지_전송_시_참여자_세션에_ROOM_MESSAGE_SUMMARY_UPDATED가_전송되고_저장된_메시지_정보와_일치한다()
                throws Exception {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            Space space = fixture.savedChatRoomBy("title", List.of(first, second));
            Long spaceId = space.getId();

            List<String> firstMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

            CountDownLatch latch = new CountDownLatch(1); // ROOM_MESSAGE_SUMMARY_UPDATED
            List<String> secondMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty()
                            && !websocketSessionManager.getSessionBy(secondId).isEmpty());

            String message = "hello summary";
            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(spaceId)
                    .message(message)
                    .build();

            // when
            spaceService.broadCastMessage(firstId, sendChat);

            // then
            boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            JsonNode summaryNode = findMessageType(secondMessages, "ROOM_MESSAGE_SUMMARY_UPDATED");

            Message savedMessage = messageRepository.findAll().stream()
                    .filter(m -> m.getContent().equals(message))
                    .findFirst()
                    .orElseThrow();

            assertThat(summaryNode.get("chatRoomId").asLong()).isEqualTo(spaceId);
            assertThat(summaryNode.get("lastChatId").asLong()).isEqualTo(savedMessage.getId());
            assertThat(summaryNode.get("lastMessage").asText()).isEqualTo(message);
            assertThat(summaryNode.get("createdDate").isNull()).isFalse();
        }

        @Test
        @DisplayName("Space에 참여하지 않은 멤버는 메시지 전송 시 ROOM_MESSAGE_SUMMARY_UPDATED를 받지 않는다.")
        void Space에_참여하지_않은_멤버는_ROOM_MESSAGE_SUMMARY_UPDATED를_받지_않는다() throws Exception {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            // outsider는 Space 참여자가 아니다
            String outsiderUsername = "outsider";
            Member outsider = memberFixture.saveEncryptPasswordBy(outsiderUsername);
            Long outsiderId = outsider.getId();

            Space space = fixture.savedChatRoomBy("title", List.of(first, second));
            Long spaceId = space.getId();

            List<String> firstMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

            CountDownLatch latch = new CountDownLatch(1); // second의 ROOM_MESSAGE_SUMMARY_UPDATED
            List<String> secondMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);

            List<String> outsiderMessages = new CopyOnWriteArrayList<>();
            String outsiderJSessionId = memberFixture.loginRequestBy(outsiderUsername, port);
            socketFixture.connectSocket(outsiderJSessionId, outsiderId, port, outsiderMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty()
                            && !websocketSessionManager.getSessionBy(secondId).isEmpty()
                            && !websocketSessionManager.getSessionBy(outsiderId).isEmpty());

            SendChat sendChat = SendChat
                    .builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .chatRoomId(spaceId)
                    .message("hello")
                    .build();

            // when
            spaceService.broadCastMessage(firstId, sendChat);

            // then: second는 recipientMemberIds(Space participant)에 포함되어 summary를 받지만,
            // outsider는 recipientMemberIds에 애초에 포함되지 않으므로 구조적으로 아무것도 받을 수 없다.
            boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(received).isTrue();
            assertThat(containsMessageType(secondMessages, "ROOM_MESSAGE_SUMMARY_UPDATED")).isTrue();

            assertThat(containsMessageType(outsiderMessages, "ROOM_MESSAGE_SUMMARY_UPDATED")).isFalse();
        }

        private boolean containsMessageType(List<String> messages, String messageType) {
            return countMessageType(messages, messageType) > 0;
        }

        private long countMessageType(List<String> messages, String messageType) {
            return messages.stream()
                    .filter(msg -> messageType.equals(readTree(msg).get("messageType").asText()))
                    .count();
        }
    }

    @Nested
    @DisplayName("renameSpace")
    class RenameSpace {

        @Test
        @DisplayName("Space 이름 변경 시 room에 ENTER하지 않은 참여자에게도 SPACE_TITLE_CHANGED가 전송된다.")
        void Space_이름_변경_시_참여자에게_SPACE_TITLE_CHANGED가_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            Space space = fixture.savedChatRoomBy("oldTitle", List.of(first));
            Long spaceId = space.getId();

            // first가 WS 연결만 하고, room(spaceManager)에는 입장하지 않는다 — ENTER 여부와 무관하게 전송되는지 검증
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            CountDownLatch latch = new CountDownLatch(1);
            List<String> firstMessages = new CopyOnWriteArrayList<>();
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty());

            // when: 채팅방 이름 변경
            spaceService.renameSpace(firstId, spaceId, "newTitle");

            // then: SPACE_TITLE_CHANGED payload(chatRoomId/title)가 정확하다
            boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            JsonNode node = findMessageType(firstMessages, "SPACE_TITLE_CHANGED");
            assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
            assertThat(node.get("title").asText()).isEqualTo("newTitle");
        }

        @Test
        @DisplayName("Space 이름 변경 시 요청자의 모든 활성 세션이 SPACE_TITLE_CHANGED를 수신한다.")
        void Space_이름_변경_시_요청자의_모든_활성_세션이_수신한다() throws ExecutionException, InterruptedException, JsonProcessingException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            Space space = fixture.savedChatRoomBy("oldTitle", List.of(first));
            Long spaceId = space.getId();

            // first가 두 개의 세션(다중 탭/기기)으로 동시 접속
            CountDownLatch firstSessionLatch = new CountDownLatch(1);
            List<String> firstSessionMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstSessionMessages, firstSessionLatch);

            CountDownLatch secondSessionLatch = new CountDownLatch(1);
            List<String> secondSessionMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(secondJSessionId, firstId, port, secondSessionMessages, secondSessionLatch);
            awaitCondition(
                    "two WebSocket sessions registration",
                    () -> websocketSessionManager.getSessionBy(firstId).size() == 2);

            // when: 요청자 본인이 rename 수행
            spaceService.renameSpace(firstId, spaceId, "newTitle");

            // then: 두 세션 모두 SPACE_TITLE_CHANGED를 수신한다
            assertThat(firstSessionLatch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(secondSessionLatch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            assertThat(containsMessageType(firstSessionMessages, "SPACE_TITLE_CHANGED")).isTrue();
            assertThat(containsMessageType(secondSessionMessages, "SPACE_TITLE_CHANGED")).isTrue();
        }

        @Test
        @DisplayName("같은 title로 rename을 요청하면 SPACE_TITLE_CHANGED가 발행되지 않는다.")
        void 같은_title로_rename하면_SPACE_TITLE_CHANGED가_발행되지_않는다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            Space space = fixture.savedChatRoomBy("sameTitle", List.of(first));
            Long spaceId = space.getId();

            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            List<String> firstMessages = new CopyOnWriteArrayList<>();
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty());

            // when: 기존과 동일한 title로 rename 요청
            spaceService.renameSpace(firstId, spaceId, "sameTitle");

            // then: SPACE_TITLE_CHANGED가 발행되지 않는다 (다른 unrelated 메시지 유무는 이 테스트의 관심사가 아니다)
            Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
            assertThat(containsMessageType(firstMessages, "SPACE_TITLE_CHANGED")).isFalse();
        }

        @Test
        @DisplayName("Space 비참여자는 rename 시 SPACE_TITLE_CHANGED를 수신하지 않는다.")
        void Space_비참여자는_SPACE_TITLE_CHANGED를_수신하지_않는다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            // outsider는 Space 참여자가 아니다
            String outsiderUsername = "outsider";
            Member outsider = memberFixture.saveEncryptPasswordBy(outsiderUsername);
            Long outsiderId = outsider.getId();

            Space space = fixture.savedChatRoomBy("oldTitle", List.of(first));
            Long spaceId = space.getId();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> firstMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);

            List<String> outsiderMessages = new CopyOnWriteArrayList<>();
            String outsiderJSessionId = memberFixture.loginRequestBy(outsiderUsername, port);
            socketFixture.connectSocket(outsiderJSessionId, outsiderId, port, outsiderMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty()
                            && !websocketSessionManager.getSessionBy(outsiderId).isEmpty());

            // when
            spaceService.renameSpace(firstId, spaceId, "newTitle");

            // then: 참여자 first는 SPACE_TITLE_CHANGED를 수신하고, 비참여자 outsider는 수신하지 않는다
            assertThat(latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(containsMessageType(firstMessages, "SPACE_TITLE_CHANGED")).isTrue();

            Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
            assertThat(containsMessageType(outsiderMessages, "SPACE_TITLE_CHANGED")).isFalse();
        }

        private boolean containsMessageType(List<String> messages, String messageType) {
            return messages.stream()
                    .anyMatch(msg -> messageType.equals(readTree(msg).get("messageType").asText()));
        }
    }

    @Nested
    @DisplayName("inviteMembers")
    class InviteMembers {

        @Test
        @DisplayName("신규 초대자는 room에 ENTER하지 않아도 SPACE_INVITED를 수신한다.")
        void 신규_초대자는_room에_ENTER하지_않아도_SPACE_INVITED를_수신한다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            // 초기 방: first만 참여
            Space space = fixture.savedChatRoomBy("title", List.of(first));
            Long spaceId = space.getId();

            // second는 WS 연결만 하고 room(spaceManager)에는 입장하지 않는다
            String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
            CountDownLatch latch = new CountDownLatch(1);
            List<String> secondMessages = new CopyOnWriteArrayList<>();
            socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(secondId).isEmpty());

            // when: first가 second를 초대
            spaceService.inviteMembers(firstId, spaceId, Set.of(secondId));

            // then: second가 SPACE_INVITED를 수신하고, payload에는 messageType 필드만 존재한다
            boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            JsonNode node = findMessageType(secondMessages, "SPACE_INVITED");
            assertThat(node.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("신규 초대자의 모든 활성 세션이 SPACE_INVITED를 수신한다.")
        void 신규_초대자의_모든_활성_세션이_SPACE_INVITED를_수신한다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            Space space = fixture.savedChatRoomBy("title", List.of(first));
            Long spaceId = space.getId();

            // second가 두 개의 세션(다중 탭/기기)으로 동시 접속
            CountDownLatch firstSessionLatch = new CountDownLatch(1);
            List<String> firstSessionMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId1 = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId1, secondId, port, firstSessionMessages, firstSessionLatch);

            CountDownLatch secondSessionLatch = new CountDownLatch(1);
            List<String> secondSessionMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId2 = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId2, secondId, port, secondSessionMessages, secondSessionLatch);
            awaitCondition(
                    "two WebSocket sessions registration",
                    () -> websocketSessionManager.getSessionBy(secondId).size() == 2);

            // when
            spaceService.inviteMembers(firstId, spaceId, Set.of(secondId));

            // then: 두 세션 모두 SPACE_INVITED를 수신한다
            assertThat(firstSessionLatch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(secondSessionLatch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            assertThat(containsMessageType(firstSessionMessages, "SPACE_INVITED")).isTrue();
            assertThat(containsMessageType(secondSessionMessages, "SPACE_INVITED")).isTrue();
        }

        @Test
        @DisplayName("이미 참여 중인 멤버를 다시 초대하면 SPACE_INVITED가 발행되지 않는다.")
        void 이미_참여_중인_멤버만_다시_초대하면_SPACE_INVITED가_발행되지_않는다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            // second는 이미 참여 중
            Space space = fixture.savedChatRoomBy("title", List.of(first, second));
            Long spaceId = space.getId();

            List<String> secondMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(secondId).isEmpty());

            // when: 이미 참여 중인 second를 다시 초대
            spaceService.inviteMembers(firstId, spaceId, Set.of(secondId));

            // then: SPACE_INVITED가 발행되지 않는다
            Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
            assertThat(containsMessageType(secondMessages, "SPACE_INVITED")).isFalse();
        }

        @Test
        @DisplayName("초대 대상에 신규 멤버와 기존 참여자가 섞여 있으면 신규 멤버만 SPACE_INVITED를 수신한다.")
        void 신규_멤버와_기존_참여자가_섞인_초대에서는_신규_멤버만_수신한다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            String secondUsername = "second"; // 이미 참여 중
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            String thirdUsername = "third"; // 신규 초대 대상
            Member third = memberFixture.saveEncryptPasswordBy(thirdUsername);
            Long thirdId = third.getId();

            Space space = fixture.savedChatRoomBy("title", List.of(first, second));
            Long spaceId = space.getId();

            List<String> secondMessages = new CopyOnWriteArrayList<>();
            String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
            socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, new CountDownLatch(1));

            CountDownLatch thirdLatch = new CountDownLatch(1);
            List<String> thirdMessages = new CopyOnWriteArrayList<>();
            String thirdJSessionId = memberFixture.loginRequestBy(thirdUsername, port);
            socketFixture.connectSocket(thirdJSessionId, thirdId, port, thirdMessages, thirdLatch);
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(secondId).isEmpty()
                            && !websocketSessionManager.getSessionBy(thirdId).isEmpty());

            // when: second(기존 참여자)와 third(신규)를 함께 초대
            spaceService.inviteMembers(firstId, spaceId, Set.of(secondId, thirdId));

            // then: third만 SPACE_INVITED를 수신하고, second는 수신하지 않는다
            assertThat(thirdLatch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(containsMessageType(thirdMessages, "SPACE_INVITED")).isTrue();

            Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
            assertThat(containsMessageType(secondMessages, "SPACE_INVITED")).isFalse();
        }

        @Test
        @DisplayName("이번 초대 대상이 아닌 사용자는 기존 participant인지 완전 outsider인지와 무관하게 SPACE_INVITED를 수신하지 않는다.")
        void 이번_초대_대상이_아닌_사용자는_participant_여부와_무관하게_SPACE_INVITED를_수신하지_않는다() throws ExecutionException, InterruptedException {
            // given
            String firstUsername = "first";
            Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
            Long firstId = first.getId();

            // second는 이번 요청의 실제 초대 대상(신규)이다 — second의 수신 여부는 기본 신규 초대 테스트가 이미 보호하므로 여기서는 다시 검증하지 않는다
            String secondUsername = "second";
            Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
            Long secondId = second.getId();

            // outsider는 Space 참여자도, 이번 초대 대상도 아니다
            String outsiderUsername = "outsider";
            Member outsider = memberFixture.saveEncryptPasswordBy(outsiderUsername);
            Long outsiderId = outsider.getId();

            // 초기 방: first만 참여
            Space space = fixture.savedChatRoomBy("title", List.of(first));
            Long spaceId = space.getId();

            List<String> firstMessages = new CopyOnWriteArrayList<>();
            String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
            socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

            List<String> outsiderMessages = new CopyOnWriteArrayList<>();
            String outsiderJSessionId = memberFixture.loginRequestBy(outsiderUsername, port);
            socketFixture.connectSocket(outsiderJSessionId, outsiderId, port, outsiderMessages, new CountDownLatch(1));
            awaitCondition(
                    "WebSocket session registration",
                    () -> !websocketSessionManager.getSessionBy(firstId).isEmpty()
                            && !websocketSessionManager.getSessionBy(outsiderId).isEmpty());

            // when: first가 second를 초대
            spaceService.inviteMembers(firstId, spaceId, Set.of(secondId));

            // then: 기존 참여자 first도, 완전 무관한 outsider도 SPACE_INVITED를 수신하지 않는다
            Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
            assertThat(containsMessageType(firstMessages, "SPACE_INVITED")).isFalse();
            assertThat(containsMessageType(outsiderMessages, "SPACE_INVITED")).isFalse();
        }

        private boolean containsMessageType(List<String> messages, String messageType) {
            return messages.stream()
                    .anyMatch(msg -> messageType.equals(readTree(msg).get("messageType").asText()));
        }
    }

    private JsonNode readTree(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode findMessageType(List<String> messages, String messageType) {
        return messages.stream()
                .map(this::readTree)
                .filter(node -> messageType.equals(node.get("messageType").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(messageType + "가 수신되지 않았다."));
    }
}
