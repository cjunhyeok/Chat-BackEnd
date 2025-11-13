package com.chat.service;

import com.chat.entity.ChatRoom;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

//@Transactional
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ChatRoomServiceSocketTest {

    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private SocketFixture socketFixture;
    @Autowired
    private ChatRoomService chatRoomService;
    @LocalServerPort
    private int port;
    private WebSocketClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("채팅 내역이 없는 채팅방에 연결한다.")
    void connectChatRoomSocketTest() throws Exception {
        // given
        String username = "username";
        Member encryptMember = memberFixture.saveEncryptPasswordBy(username);
        Long encryptMemberId = encryptMember.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(encryptMember);
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();
//        fixture.flushAllData();

        String JSessionId = memberFixture.loginRequestBy(username, port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                session.getAttributes().put(SessionConst.SESSION_ID, encryptMemberId);
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                receivedMessages.add(message.getPayload());
                latch.countDown();
            }
        };

        client = new StandardWebSocketClient();
        client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        // when
        chatRoomService.connectChatRoomSocket(encryptMemberId, chatRoomId);

        // then
        boolean messageReceived = latch.await(3, TimeUnit.SECONDS);
        assertTrue(messageReceived, "EnterChatRoom 메시지를 수신해야 합니다.");
        String payload = receivedMessages.get(0);
        assertTrue(payload.contains("\"messageType\":\"CHAT_ENTER\""));
    }

    @Test
    @DisplayName("채팅방에 메시지를 전송한다.")
    void simpleBroadCastMessageTest() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String first = "first";
        Member firstMember = memberFixture.saveEncryptPasswordBy(first);
        String firstJSessionId = memberFixture.loginRequestBy(first, port);
        Long firstMemberId = firstMember.getId();

        String second = "second";
        Member secondMember = memberFixture.saveEncryptPasswordBy(second);
        String secondJSessionId = memberFixture.loginRequestBy(second, port);
        Long secondMemberId = secondMember.getId();

        CountDownLatch latch = new CountDownLatch(2);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstMemberId, port, firstMessages, latch);
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondMemberId, port, secondMessages, latch);

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        chatRoomService.connectChatRoomSocket(firstMemberId, chatRoomId);
        chatRoomService.connectChatRoomSocket(secondMemberId, chatRoomId);

        String message = "message";
        fixture.savedSimpleChat(message, firstMember, chatRoom);

        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .senderId(firstMemberId)
                .senderNickname(firstMember.getNickname())
                .chatRoomId(chatRoomId)
                .message(message)
                .build();

        // when
        chatRoomService.broadCastMessage(sendChat);

        // then
        boolean messageReceived = latch.await(3, TimeUnit.SECONDS);
        assertTrue(messageReceived, "EnterChatRoom 메시지를 수신해야 합니다.");
        String payload = secondMessages.get(1);
        objectMapper.addMixIn(SendChat.class, SendChatIgnoreMixIn.class);
        SendChat chatData = objectMapper.readValue(payload, SendChat.class);
        assertThat(payload).isNotEmpty();
        assertThat(chatData.getChatId()).isNotNull();
        assertThat(chatData.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("채팅방 참여자들에게 메시지를 전송한다.")
    void simpleBroadcastToChatRoomMembersTest() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String first = "firstUsername";
        Member firstMember = memberFixture.saveEncryptPasswordBy(first);
        String firstJSessionId = memberFixture.loginRequestBy(first, port);
        Long firstMemberId = firstMember.getId();

        String second = "secondUsername";
        Member secondMember = memberFixture.saveEncryptPasswordBy(second);
        String secondJSessionId = memberFixture.loginRequestBy(second, port);
        Long secondMemberId = secondMember.getId();

        CountDownLatch latch = new CountDownLatch(2);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstMemberId, port, firstMessages, latch);
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondMemberId, port, secondMessages, latch);

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        // when
        chatRoomService.broadcastToChatRoomMembers(chatRoomId);

        // then
        boolean messageReceived = latch.await(3, TimeUnit.SECONDS);
        String payload = secondMessages.get(0);
        objectMapper.addMixIn(UpdateChatRoom.class, SendChatIgnoreMixIn.class);
        UpdateChatRoom chatData = objectMapper.readValue(payload, UpdateChatRoom.class);
        assertThat(payload).isNotEmpty();
        assertThat(chatData.getChatRoomId()).isEqualTo(chatRoomId);
    }

    public abstract class SendChatIgnoreMixIn {
        @JsonIgnore
        private LocalDateTime createDate;
    }
}