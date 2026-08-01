package com.chat.socket.handler;

import com.chat.entity.Discussion;
import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.DiscussionRepository;
import com.chat.service.dtos.chat.EnterRoomRequest;
import com.chat.service.dtos.chat.ReadUpToRequest;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.SendDiscussionMessage;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTextSocketHandlerTest {

    // StandardWebSocketClient.execute().get()은 클라이언트 측 연결 완료만 보장한다.
    // 서버의 afterConnectionEstablished()는 별도 Tomcat I/O 스레드에서 실행되므로
    // 클라이언트 Future 완료 시점에 websocketSessionManager 세션 등록이 완료됐다는 보장이 없다.
    // getSessionBy() 호출 전 서버 세션 등록 완료를 기다리기 위해 짧게 대기한다.
    private static final long SERVER_SESSION_REGISTER_WAIT_MS = 300;

    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private DiscussionRepository discussionRepository;

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

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(2);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, chatRoomId);

        ObjectMapper objectMapper = new ObjectMapper();
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(chatRoomId)
                .message("message")
                .build();
        String chat = objectMapper.writeValueAsString(sendChat);

        // when
        session.sendMessage(new TextMessage(chat));

        // then: CHAT_ENTER 제거 → CHAT_MESSAGE + ROOM_MESSAGE_SUMMARY_UPDATED = 2개
        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedMessages).hasSize(2);
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

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        // ENTER_ROOM을 보내지 않아 chatRoomManager에 세션이 없는 상태

        ObjectMapper objectMapper = new ObjectMapper();
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(chatRoomId)
                .message("blocked message")
                .clientMessageId("client-temp-1")
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
        assertThat(node.get("clientMessageId").asText()).isEqualTo("client-temp-1");
        assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).isEmpty();
    }

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

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        // when
        ObjectMapper objectMapper = new ObjectMapper();
        EnterRoomRequest enterRoom = EnterRoomRequest.builder()
                .messageType(MessageType.ENTER_ROOM)
                .chatRoomId(spaceId)
                .build();
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(enterRoom)));

        // then: ENTER_ROOM → validateParticipant → getWrappedSession → addSessionToSpace → ENTER_ROOM_ACK
        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        assertThat(spaceManager.getWebSocketSessionBy(spaceId)).isNotEmpty();
        assertThat(spaceManager.isSpaceActive(memberId, spaceId)).isTrue();

        JsonNode node = objectMapper.readTree(receivedMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("ENTER_ROOM_ACK");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
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

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + intruderJSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(intruder.getId(), receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

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

    @Test
    @DisplayName("잘못된 형식의 메시지를 전송하면 requestType 없이 INVALID_MESSAGE 에러 응답을 받는다.")
    void 잘못된_형식의_메시지를_전송하면_requestType_없이_INVALID_MESSAGE_에러_응답을_받는다() throws ExecutionException, InterruptedException, IOException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);

        String JSessionId = memberFixture.loginRequestBy(username, port);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(member.getId(), receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

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

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, spaceId);

        assertThat(spaceManager.getWebSocketSessionBy(spaceId)).isNotEmpty();
        assertThat(spaceManager.isSpaceActive(memberId, spaceId)).isTrue();

        // when: 세션 종료
        session.close(CloseStatus.NORMAL);

        // TestWebSocketHandler는 afterConnectionClosed()를 구현하지 않으므로
        // 이 latch는 countdown되지 않는다.
        // session.close() 후 서버의 afterConnectionClosed 처리 완료를 기다리는 timed wait로 사용한다.
        latch.await(2, TimeUnit.SECONDS);

        // websocketSessionManager, spaceManager chatRooms, spaceManager sessionStates 전체 정리 검증
        assertThat(websocketSessionManager.getSessionBy(memberId)).isEmpty();
        assertThat(spaceManager.getWebSocketSessionBy(spaceId)).isEmpty();
        assertThat(spaceManager.isSpaceActive(memberId, spaceId)).isFalse();
    }

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
        Discussion discussion = discussionRepository.save(Discussion.of(rootMessage));

        String JSessionId = memberFixture.loginRequestBy(username, port);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(member.getId(), receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession clientSession = client.execute(
                handler, headers, URI.create("ws://localhost:" + port + "/ws/chat")).get();

        // 서버 세션을 Space에 등록
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
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

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
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
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
        assertThat(receivedMessages).hasSize(1);

        JsonNode readEventBatchNode = objectMapper.readTree(receivedMessages.get(0));
        assertThat(readEventBatchNode.get("messageType").asText()).isEqualTo("READ_EVENT_BATCH");

        JsonNode readItemNode = readEventBatchNode.get("reads").get(0);
        assertThat(readItemNode.get("memberId").asLong()).isEqualTo(memberId);
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

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

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

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
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