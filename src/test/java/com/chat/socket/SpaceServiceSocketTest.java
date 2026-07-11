package com.chat.socket;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.MessageRepository;
import com.chat.service.MessageService;
import com.chat.service.SpaceService;
import com.chat.service.dtos.chat.BroadcastChat;
import com.chat.service.dtos.chat.RoomActiveRequest;
import com.chat.service.dtos.chat.RoomMessageSummaryUpdated;
import com.chat.service.dtos.chat.SendChat;
import com.chat.socket.event.PublishMessageEvent;
import com.chat.socket.listener.MessageBroadcastListener;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SpaceServiceSocketTest {

    // StandardWebSocketClient.execute().get()은 클라이언트 측 연결 완료만 보장한다.
    // 서버의 afterConnectionEstablished()는 별도 Tomcat I/O 스레드에서 실행되므로
    // 클라이언트 Future 완료 시점에 websocketSessionManager 세션 등록이 완료됐다는 보장이 없다.
    // getSessionBy() 호출 전 서버 세션 등록 완료를 기다리기 위해 짧게 대기한다.
    private static final long SERVER_SESSION_REGISTER_WAIT_MS = 300;
    private static final long BROADCAST_TIMEOUT_SECONDS = 3;

    @Autowired
    private SpaceService spaceService;
    @Autowired
    private MessageService messageService;

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
    @Autowired
    private MessageBroadcastListener messageBroadcastListener;

    @LocalServerPort
    private int port;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        fixture.deleteAllData();
        spaceManager.clearAll();
        websocketSessionManager.clearAll();
    }

    @Test
    @DisplayName("메시지가 없는 Space 소켓에 연결하면 세션이 방에 등록된다.")
    void 메시지가_없는_Space_소켓에_연결하면_세션이_방에_등록된다() throws ExecutionException, InterruptedException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);
        Long memberId = member.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(member);
        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        String JSESSIONID = memberFixture.loginRequestBy(username, port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, memberId, port, receivedMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, spaceId);

        // then: 세션만 등록됨, 클라이언트로 전송되는 메시지 없음
        assertThat(receivedMessages).isEmpty();
        assertThat(spaceManager.getWebSocketSessionBy(spaceId)).contains(serverSession);
    }

    @Test
    @DisplayName("메시지가 있는 Space 소켓에 연결하면 세션이 방에 등록된다.")
    void 메시지가_있는_Space_소켓에_연결하면_세션이_방에_등록된다() throws ExecutionException, InterruptedException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        messageService.saveMessage(firstId, spaceId, "firstChat");
        messageService.saveMessage(secondId, spaceId, "secondChat");

        String JSESSIONID = memberFixture.loginRequestBy("first", port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, firstId, port, receivedMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, spaceId);

        // then: CHAT_ENTER 미전송, 세션 등록 여부만 확인
        assertThat(receivedMessages).isEmpty();
        Set<WebSocketSession> webSocketSessions = spaceManager.getWebSocketSessionBy(spaceId);
        assertThat(webSocketSessions).hasSize(1);
        Collection<WebSocketSession> memberSessions = websocketSessionManager.getSessionBy(firstId);
        assertThat(memberSessions).isNotEmpty();
        assertThat(webSocketSessions.containsAll(memberSessions)).isTrue();
    }

    @Test
    @DisplayName("Space에 연결된 모든 참여자에게 메시지가 전송된다.")
    void Space에_연결된_모든_참여자에게_메시지가_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy("first", port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        CountDownLatch latch = new CountDownLatch(1);
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy("second", port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

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

        // then: CHAT_MESSAGE가 second에 도착할 때까지 대기
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(secondMessages).isNotEmpty();

        String payload = secondMessages.get(0);
        JsonNode node = objectMapper.readTree(payload);
        assertThat(node.get("messageType").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(node.get("message").asText()).isEqualTo(message);
        assertThat(node.get("senderId").asLong()).isEqualTo(firstId);
        assertThat(node.get("senderNickname").asText()).isEqualTo(first.getNickname());
        assertThat(node.get("chatId").isNull()).isFalse();
        assertThat(node.has("unreadMemberCount")).isTrue();
        Long messageId = node.get("chatId").asLong();
        Message foundMessage = messageRepository.findById(messageId).get();
        assertThat(foundMessage.getContent()).isEqualTo(message);
    }

    @Test
    @DisplayName("clientMessageId를 포함해 메시지를 전송하면 저장 및 echo payload에 그대로 포함된다.")
    void clientMessageId를_포함해_메시지를_전송하면_저장_및_echo_payload에_포함된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        CountDownLatch latch = new CountDownLatch(1);
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

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
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(secondMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(secondMessages.get(0));
        assertThat(node.get("clientMessageId").asText()).isEqualTo(clientMessageId);

        Long messageId = node.get("chatId").asLong();
        Message foundMessage = messageRepository.findById(messageId).get();
        assertThat(foundMessage.getClientMessageId()).isEqualTo(clientMessageId);
    }

    @Test
    @DisplayName("같은 clientMessageId로 두 번 전송하면 중복 저장 없이 기존 메시지 정보로 CHAT_MESSAGE가 응답된다.")
    void 같은_clientMessageId로_두_번_전송하면_중복_저장_없이_기존_메시지_정보로_응답된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space space = fixture.savedChatRoomBy("title", List.of(first, second));
        Long spaceId = space.getId();

        // first는 Space 참여자이자 room 등록 세션이라 호출마다 CHAT_MESSAGE + ROOM_MESSAGE_SUMMARY_UPDATED를 함께 받는다 (총 4건)
        CountDownLatch latch = new CountDownLatch(4);
        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

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

        // 두 번째 전송의 CHAT_MESSAGE payload도 기존 메시지 정보(senderNickname 포함)로 정상 생성된다
        // (saveMessageEntity가 반환한 기존 Message의 member는 지연 프록시이므로, 이 지점에서 lazy load가 발생해도 예외 없이 값이 채워져야 한다)
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        List<String> chatMessagePayloads = firstMessages.stream()
                .filter(msg -> "CHAT_MESSAGE".equals(readTree(msg).get("messageType").asText()))
                .collect(Collectors.toList());
        assertThat(chatMessagePayloads).hasSize(2);

        for (String payload : chatMessagePayloads) {
            JsonNode node = objectMapper.readTree(payload);
            assertThat(node.get("chatId").asLong()).isEqualTo(savedMessage.getId());
            assertThat(node.get("clientMessageId").asText()).isEqualTo(clientMessageId);
            assertThat(node.get("message").asText()).isEqualTo(message);
            assertThat(node.get("senderNickname").asText()).isEqualTo(first.getNickname());
            assertThat(node.get("createdDate").isNull()).isFalse();
        }
    }

    @Test
    @DisplayName("메시지 전송 시 senderId와 senderNickname은 세션과 DB 기준으로 설정된다.")
    void 메시지_전송_시_senderId와_senderNickname은_세션과_DB_기준으로_설정된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);
        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        CountDownLatch latch = new CountDownLatch(1);
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

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

        // when: loginMemberId = firstId (세션 기준값)
        spaceService.broadCastMessage(firstId, sendChat);

        // then
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(secondMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(secondMessages.get(0));

        // senderId는 파라미터로 전달된 firstId (세션 값)
        assertThat(node.get("senderId").asLong()).isEqualTo(firstId);
        // senderNickname은 DB에서 조회된 값
        assertThat(node.get("senderNickname").asText()).isEqualTo(first.getNickname());
        // Chat이 DB에 firstId 기준으로 저장되었는지 확인
        Long savedMessageId = node.get("chatId").asLong();
        Message savedMessage = messageRepository.findById(savedMessageId).orElseThrow();
        assertThat(savedMessage.getMember().getId()).isEqualTo(firstId);
        // clientMessageId 없는 기존 요청도 정상 처리되며, echo payload의 clientMessageId는 null
        assertThat(node.get("clientMessageId").isNull()).isTrue();
        assertThat(savedMessage.getClientMessageId()).isNull();
    }

    @Test
    @DisplayName("채팅 내역 조회 시 방에 접속 중인 세션에 READ_EVENT만 전송되고 UPDATE_CHAT_ROOM은 전송되지 않는다.")
    void 채팅_내역_조회_시_방에_접속_중인_세션에_READ_EVENT만_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        // second가 아직 방에 없는 상태에서 first가 메시지 전송 → second.isRead=false
        messageService.saveMessage(firstId, spaceId, "hello");

        // second가 WS 연결 및 방 입장
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(1); // READ_EVENT (UPDATE_CHAT_ROOM 없음)
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);

        // when: second가 채팅 내역 조회 → updatedCount > 0 이면 READ_EVENT 발행 (읽음 처리 경로에서는 UPDATE_CHAT_ROOM을 더 이상 발행하지 않음)
        messageService.findMessageHistory(spaceId, secondId, null);

        // then: READ_EVENT 수신 대기
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        // latch 통과 이후에도 지연 도착하는 메시지가 없는지 확인하기 위해 짧게 대기
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        List<String> messageTypes = secondMessages.stream()
                .map(msg -> {
                    try {
                        return objectMapper.readTree(msg).get("messageType").asText();
                    } catch (Exception e) {
                        return "";
                    }
                })
                .collect(Collectors.toList());
        assertThat(messageTypes).containsExactly("READ_EVENT");
        assertThat(messageTypes).doesNotContain("UPDATE_CHAT_ROOM");

        // READ_EVENT의 memberId, chatRoomId, previousLastReadChatId, currentLastReadChatId 검증
        String readEventPayload = secondMessages.stream()
                .filter(msg -> msg.contains("READ_EVENT"))
                .findFirst()
                .orElseThrow();
        JsonNode readEventNode = objectMapper.readTree(readEventPayload);
        assertThat(readEventNode.get("memberId").asLong()).isEqualTo(secondId);
        assertThat(readEventNode.get("chatRoomId").asLong()).isEqualTo(spaceId);
        assertThat(readEventNode.get("previousLastReadChatId").isNull()).isTrue();
        assertThat(readEventNode.get("currentLastReadChatId").isNull()).isFalse();
    }

    @Test
    @DisplayName("채팅 내역 조회 시 READ_EVENT에 이전 방문의 lastReadMessageId가 포함된다.")
    void 채팅_내역_조회_시_READ_EVENT에_이전_방문의_lastReadMessageId가_포함된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space space = fixture.savedChatRoomBy("title", List.of(first, second));
        Long spaceId = space.getId();

        // first가 첫 번째 메시지 전송 → second.isRead=false
        Long firstMessageId = messageService.saveMessage(firstId, spaceId, "first message");

        // second 첫 번째 입장: firstChat 읽음 처리 (lastReadChatId=null, updatedCount=1)
        messageService.findMessageHistory(spaceId, secondId, null);

        // first가 두 번째 메시지 전송 → second.isRead=false
        messageService.saveMessage(firstId, spaceId, "second message");

        // second WS 연결 및 방 입장
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(1); // READ_EVENT (읽음 처리 경로는 UPDATE_CHAT_ROOM을 발행하지 않음)
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);

        // when: second 두 번째 채팅 내역 조회 → lastReadChatId = firstMessageId (이전 방문 시 firstChat까지 읽었음)
        messageService.findMessageHistory(spaceId, secondId, null);

        // then: READ_EVENT에 lastReadChatId 포함 검증
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        String readEventPayload = secondMessages.stream()
                .filter(msg -> msg.contains("READ_EVENT"))
                .findFirst()
                .orElseThrow();
        JsonNode readEventNode = objectMapper.readTree(readEventPayload);
        assertThat(readEventNode.get("memberId").asLong()).isEqualTo(secondId);
        assertThat(readEventNode.get("previousLastReadChatId").asLong()).isEqualTo(firstMessageId);
    }

    @Test
    @DisplayName("Space 나가기 시 남은 참여자에게 UPDATE_CHAT_ROOM이 전송된다.")
    void Space_나가기_시_남은_참여자에게_UPDATE_CHAT_ROOM이_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space space = fixture.savedChatRoomBy("title", List.of(first, second));
        Long spaceId = space.getId();

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);

        // when: second가 채팅방 퇴장
        spaceService.leaveSpace(secondId, spaceId);

        // then: 남은 first에게 UPDATE_CHAT_ROOM 전송
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
    }

    @Test
    @DisplayName("Space 이름 변경 시 참여자에게 변경된 제목의 UPDATE_CHAT_ROOM이 전송된다.")
    void Space_이름_변경_시_참여자에게_변경된_제목의_UPDATE_CHAT_ROOM이_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        Space space = fixture.savedChatRoomBy("oldTitle", List.of(first));
        Long spaceId = space.getId();

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);

        // when: 채팅방 이름 변경
        spaceService.renameSpace(firstId, spaceId, "newTitle");

        // then: UPDATE_CHAT_ROOM에 변경된 title 포함
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
        assertThat(node.get("title").asText()).isEqualTo("newTitle");
    }

    @Test
    @DisplayName("멤버 초대 시 기존 참여자에게 UPDATE_CHAT_ROOM이 전송된다.")
    void 멤버_초대_시_기존_참여자에게_UPDATE_CHAT_ROOM이_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
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

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);

        // when: second를 초대
        spaceService.inviteMembers(firstId, spaceId, Set.of(secondId));

        // then: 기존 참여자 first에게 UPDATE_CHAT_ROOM 전송
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
    }

    @Test
    @DisplayName("ROOM_ACTIVE 전송 시 unread가 있으면 READ_EVENT만 소켓으로 전달되고 UPDATE_CHAT_ROOM은 전달되지 않는다.")
    void ROOM_ACTIVE_전송_시_unread가_있으면_READ_EVENT만_전달된다()
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

        // second 연결 전 first가 메시지 전송 → second cursor=null (unread=1)
        messageService.saveMessage(firstId, spaceId, "hello");

        // second WS 연결 (latch=1: READ_EVENT, 읽음 처리 경로는 더 이상 UPDATE_CHAT_ROOM을 발행하지 않음)
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> secondMessages = new ArrayList<>();
        WebSocketSession secondClientSession =
                socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        // 기존 패턴과 동일: addSessionToSpace 직접 호출로 Space 세션 등록
        WebSocketSession secondServerSession =
                websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);

        // when: second 클라이언트가 ROOM_ACTIVE WS 메시지 전송
        RoomActiveRequest roomActive = RoomActiveRequest.builder()
                .messageType(MessageType.ROOM_ACTIVE)
                .chatRoomId(spaceId)
                .build();
        secondClientSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(roomActive)));

        // then: 3초 안에 READ_EVENT 수신
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        // latch 통과 이후에도 지연 도착하는 메시지가 없는지 확인하기 위해 짧게 대기
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        List<String> messageTypes = secondMessages.stream()
                .map(msg -> {
                    try {
                        return objectMapper.readTree(msg).get("messageType").asText();
                    } catch (Exception e) {
                        return "";
                    }
                })
                .collect(Collectors.toList());
        assertThat(messageTypes).containsExactly("READ_EVENT");
        assertThat(messageTypes).doesNotContain("UPDATE_CHAT_ROOM");
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

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        // second는 spaceManager(room)에는 등록하지 않는다.
        // ROOM_MESSAGE_SUMMARY_UPDATED는 websocketSessionManager 기준 멤버 세션으로 전송되므로
        // room 등록과 무관하게 도착해야 한다. 메시지 생성 경로는 더 이상 UPDATE_CHAT_ROOM을 발행하지 않는다.
        CountDownLatch latch = new CountDownLatch(1); // ROOM_MESSAGE_SUMMARY_UPDATED
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

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

        JsonNode summaryNode = secondMessages.stream()
                .map(this::readTree)
                .filter(node -> "ROOM_MESSAGE_SUMMARY_UPDATED".equals(node.get("messageType").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ROOM_MESSAGE_SUMMARY_UPDATED가 수신되지 않았다."));

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
    @DisplayName("메시지 전송 시 방에 접속 중인 참여자에게 CHAT_MESSAGE와 ROOM_MESSAGE_SUMMARY_UPDATED만 전송되고 UPDATE_CHAT_ROOM은 전송되지 않는다.")
    void 메시지_전송_시_방에_접속_중인_참여자에게_CHAT_MESSAGE와_ROOM_MESSAGE_SUMMARY_UPDATED만_전송된다()
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

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        CountDownLatch latch = new CountDownLatch(2); // CHAT_MESSAGE + ROOM_MESSAGE_SUMMARY_UPDATED (UPDATE_CHAT_ROOM 없음)
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);
        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);

        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(spaceId)
                .message("hello")
                .build();

        // when
        spaceService.broadCastMessage(firstId, sendChat);

        // then: 메시지 생성 경로는 CHAT_MESSAGE + ROOM_MESSAGE_SUMMARY_UPDATED만 전송하고 UPDATE_CHAT_ROOM은 더 이상 발행하지 않는다
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        // latch 통과 이후에도 지연 도착하는 메시지가 없는지 확인하기 위해 짧게 대기
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        List<String> messageTypes = secondMessages.stream()
                .map(msg -> readTree(msg).get("messageType").asText())
                .collect(Collectors.toList());
        assertThat(messageTypes).containsExactlyInAnyOrder("CHAT_MESSAGE", "ROOM_MESSAGE_SUMMARY_UPDATED");
        assertThat(messageTypes).doesNotContain("UPDATE_CHAT_ROOM");
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

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        CountDownLatch latch = new CountDownLatch(1); // second의 ROOM_MESSAGE_SUMMARY_UPDATED
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);

        List<String> outsiderMessages = new ArrayList<>();
        String outsiderJSessionId = memberFixture.loginRequestBy(outsiderUsername, port);
        socketFixture.connectSocket(outsiderJSessionId, outsiderId, port, outsiderMessages, new CountDownLatch(1));
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(spaceId)
                .message("hello")
                .build();

        // when
        spaceService.broadCastMessage(firstId, sendChat);

        // then: second는 summary를 받지만 Space 비참여자인 outsider는 아무것도 받지 않는다
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(secondMessages.stream().map(this::readTree))
                .anyMatch(node -> "ROOM_MESSAGE_SUMMARY_UPDATED".equals(node.get("messageType").asText()));

        assertThat(outsiderMessages).isEmpty();
    }

    @Test
    @DisplayName("publishMessageToSessions는 PublishMessageEvent.recipientMemberIds만으로 ROOM_MESSAGE_SUMMARY_UPDATED 수신 대상을 결정하고 UPDATE_CHAT_ROOM은 전송하지 않는다.")
    void publishMessageToSessions는_recipientMemberIds만으로_summary_수신_대상을_결정한다() throws Exception {
        // given: recipientMemberIds에는 second만 담아 이벤트를 직접 구성한다.
        // first는 room(spaceManager)에도, recipientMemberIds에도 포함되지 않으므로 아무것도 받지 않아야 한다.
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space space = fixture.savedChatRoomBy("title", List.of(first, second));
        Long spaceId = space.getId();

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        CountDownLatch secondLatch = new CountDownLatch(1); // ROOM_MESSAGE_SUMMARY_UPDATED만 기대
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, secondLatch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        BroadcastChat broadcastChat = BroadcastChat.builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .senderId(firstId)
                .senderNickname(first.getNickname())
                .chatRoomId(spaceId)
                .message("hello")
                .chatId(1L)
                .unreadMemberCount(0L)
                .createdDate(LocalDateTime.now())
                .build();

        RoomMessageSummaryUpdated summary = RoomMessageSummaryUpdated.builder()
                .messageType(MessageType.ROOM_MESSAGE_SUMMARY_UPDATED)
                .chatRoomId(spaceId)
                .lastChatId(1L)
                .lastMessage("hello")
                .createdDate(LocalDateTime.now())
                .build();

        // recipientMemberIds: secondId만 포함. first는 room에도 등록돼 있지 않으므로
        // broadcastChat(spaceManager 경로)도, summary(recipientMemberIds 경로)도 받지 않는다.
        PublishMessageEvent event = new PublishMessageEvent(
                broadcastChat, spaceId, summary, Set.of(secondId), System.nanoTime());

        // when
        messageBroadcastListener.publishMessageToSessions(event);

        // then: second는 recipientMemberIds에 있으므로 summary를 받는다
        assertThat(secondLatch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        List<String> secondMessageTypes = secondMessages.stream()
                .map(msg -> readTree(msg).get("messageType").asText())
                .collect(Collectors.toList());
        assertThat(secondMessageTypes).containsExactly("ROOM_MESSAGE_SUMMARY_UPDATED");

        // first는 room에도, recipientMemberIds에도 없으므로 아무것도 받지 않는다 (UPDATE_CHAT_ROOM도 전송되지 않음)
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);
        assertThat(firstMessages).isEmpty();
    }

    private JsonNode readTree(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
