package com.chat.service;

import com.chat.entity.*;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.*;
import com.chat.service.dtos.MessageHistory;
import com.chat.service.dtos.MessageHistoryResponse;
import com.chat.socket.event.PublishReadEvent;
import com.chat.socket.manager.SpaceManager;
import com.chat.utils.consts.SessionConst;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Transactional
@SpringBootTest
@RecordApplicationEvents
class MessageServiceTest {

    @Autowired
    private MessageService messageService;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private SpaceMemberRepository spaceMemberRepository;
    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private SpaceManager spaceManager;
    @Autowired
    private EntityManager em;
    @Autowired
    private DiscussionRepository discussionRepository;
    @Autowired
    private DiscussionMessageRepository discussionMessageRepository;

    @AfterEach
    void tearDown() {
        spaceManager.clearAll();
    }

    @Test
    @DisplayName("채팅 메시지를 저장한다.")
    void saveChatTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver1 = fixture.savedMemberBy("receiver1");
        Member receiver2 = fixture.savedMemberBy("receiver2");

        List<Member> participants = new ArrayList<>();
        participants.add(sender);
        participants.add(receiver1);
        participants.add(receiver2);

        Space chatRoom = fixture.savedChatRoomBy("title", participants);

        String message = "message";

        // when
        Long savedChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), message);

        // then
        Message chat = messageRepository.findById(savedChatId).get();
        assertThat(chat.getContent()).isEqualTo(message);
        assertThat(chat.getSpace()).isEqualTo(chatRoom);
        assertThat(chat.getMember()).isEqualTo(sender);
    }

    @Test
    @DisplayName("clientMessageId와 함께 채팅 메시지를 저장하면 그대로 영속화된다.")
    void saveChatWithClientMessageIdTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(sender));
        String message = "message";
        String clientMessageId = "client-uuid-1234";

        // when
        Long savedChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), message, clientMessageId);

        // then
        Message chat = messageRepository.findById(savedChatId).get();
        assertThat(chat.getClientMessageId()).isEqualTo(clientMessageId);
    }

    @Test
    @DisplayName("clientMessageId 없이 채팅 메시지를 저장하면 null로 저장된다.")
    void saveChatWithoutClientMessageIdTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(sender));
        String message = "message";

        // when
        Long savedChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), message);

        // then
        Message chat = messageRepository.findById(savedChatId).get();
        assertThat(chat.getClientMessageId()).isNull();
    }

    @Test
    @DisplayName("동일한 clientMessageId로 saveMessage를 두 번 호출하면 기존 messageId를 반환하고 새 메시지를 저장하지 않는다.")
    void saveChatWithSameClientMessageId_returnsSameMessageIdTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(sender));
        String message = "message";
        String clientMessageId = "client-uuid-retry";

        // when
        Long firstChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), message, clientMessageId);
        Long retryChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), message, clientMessageId);

        // then
        assertThat(retryChatId).isEqualTo(firstChatId);

        long savedCount = messageRepository.findAll().stream()
                .filter(m -> clientMessageId.equals(m.getClientMessageId()))
                .count();
        assertThat(savedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("clientMessageId가 null이면 saveMessage를 여러 번 호출할 때마다 새 메시지가 저장된다.")
    void saveChatWithoutClientMessageId_alwaysCreatesNewMessageTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(sender));
        String message = "message";

        // when
        Long firstChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), message);
        Long secondChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), message);

        // then
        assertThat(firstChatId).isNotEqualTo(secondChatId);
        assertThat(messageRepository.findById(firstChatId)).isPresent();
        assertThat(messageRepository.findById(secondChatId)).isPresent();
    }

    @Test
    @DisplayName("메시지의 unreadMemberCount를 조회한다.")
    void countMessageUnreadMembersTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver1 = fixture.savedMemberBy("receiver1");
        Member receiver2 = fixture.savedMemberBy("receiver2");

        List<Member> participants = new ArrayList<>();
        participants.add(sender);
        participants.add(receiver1);
        participants.add(receiver2);

        Space chatRoom = fixture.savedChatRoomBy("title", participants);

        String message = "message";

        Long savedChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), message);

        // when
        Long unreadMemberCount = messageService.countMessageUnreadMembers(savedChatId);

        // then
        assertThat(unreadMemberCount).isEqualTo(2);
    }

    @Test
    @DisplayName("채팅방의 채팅목록을 조회한다.")
    void findMessageHistoryTest() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");
        Member thirdMember = fixture.savedMemberBy("thirdMember");

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);
        participants.add(thirdMember);

        Space chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = messageService.saveMessage(firstMember.getId(), chatRoomId, "message");
        messageService.saveMessage(secondMember.getId(), chatRoomId, "secondMessage");
        messageService.saveMessage(secondMember.getId(), chatRoomId, "thirdMessage");

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, firstMember.getId(), null);

        // then
        List<MessageHistory> messages = response.getMessages();
        assertThat(messages).hasSize(3);

        // lastReadMessageId: 조회 전 firstMember가 마지막으로 읽은 메시지 = 자신이 보낸 firstChat
        assertThat(response.getLastReadMessageId()).isEqualTo(firstChatId);

        MessageHistory firstChat = messages.get(0);
        assertThat(firstChat.getChatId()).isEqualTo(firstChatId);
        assertThat(firstChat.getSenderId()).isEqualTo(firstMember.getId());
        assertThat(firstChat.getUnreadMemberCount()).isEqualTo(1L);

        // cursor 갱신 이후 count → firstMember 읽음 제외한 값
        MessageHistory secondChat = messages.get(1);
        assertThat(secondChat.getUnreadMemberCount()).isEqualTo(1L);

        MessageHistory thirdChat = messages.get(2);
        assertThat(thirdChat.getUnreadMemberCount()).isEqualTo(1L);

        assertThat(firstChat.getMessage()).isEqualTo("message");
    }

    @Test
    @DisplayName("채팅방에 메시지가 없으면 빈 리스트를 반환한다.")
    void findMessageHistoryEmptyTest() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);

        Space chatRoom = fixture.savedChatRoomBy("title", participants);

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoom.getId(), firstMember.getId(), null);

        // then
        assertThat(response.getMessages()).isEmpty();
        assertThat(response.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("채팅방 재입장 시 이미 모두 읽은 상태면 미읽음이 남지 않는다.")
    void findMessageHistory_재입장시_미읽음이_남지_않는다() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");

        Space chatRoom = fixture.savedChatRoomBy("title", List.of(firstMember, secondMember));
        Long chatRoomId = chatRoom.getId();

        messageService.saveMessage(firstMember.getId(), chatRoomId, "message");

        // 첫 번째 입장: secondMember의 미읽음 1개 → 읽음 처리
        messageService.findMessageHistory(chatRoomId, secondMember.getId(), null);

        em.flush(); em.clear();
        Long cursorAfterFirst = spaceMemberRepository
                .findLastReadMessageIdBy(secondMember.getId(), chatRoomId);
        assertThat(cursorAfterFirst).isNotNull();

        // when: 재입장 — 이미 모두 읽음 처리된 상태
        messageService.findMessageHistory(chatRoomId, secondMember.getId(), null);

        // then: 재입장 후에도 미읽음 없음
        em.flush(); em.clear();
        Long cursorAfterSecond = spaceMemberRepository
                .findLastReadMessageIdBy(secondMember.getId(), chatRoomId);
        assertThat(cursorAfterSecond).isEqualTo(cursorAfterFirst);
    }

    @Test
    @DisplayName("읽지 않은 메시지가 있으면 PublishReadEvent가 발행된다.")
    void findMessageHistory_unreadExists_publishesReadEvent(ApplicationEvents events) {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");

        Space chatRoom = fixture.savedChatRoomBy("title", List.of(firstMember, secondMember));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = messageService.saveMessage(firstMember.getId(), chatRoomId, "message");

        // when: secondMember 입장 — 미읽음 1개 존재
        messageService.findMessageHistory(chatRoomId, secondMember.getId(), null);

        // then: PublishReadEvent 1건 발행, 필드값 검증
        List<PublishReadEvent> publishedEvents = events.stream(PublishReadEvent.class).toList();
        assertThat(publishedEvents).hasSize(1);

        PublishReadEvent event = publishedEvents.get(0);
        assertThat(event.getMemberId()).isEqualTo(secondMember.getId());
        assertThat(event.getChatRoomId()).isEqualTo(chatRoomId);
        assertThat(event.getPreviousLastReadChatId()).isNull(); // 이전에 읽은 기록 없음
    }

    @Test
    @DisplayName("초기 진입 시 메시지가 PAGE_SIZE를 초과하면 hasMore=true를 반환한다.")
    void findMessageHistory_initialLoad_hasMoreTrueTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        for (int i = 0; i < 31; i++) {
            messageService.saveMessage(member.getId(), chatRoomId, "message" + i);
        }

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, member.getId(), null);

        // then
        assertThat(response.getMessages()).hasSize(30);
        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    @DisplayName("초기 진입 시 메시지가 PAGE_SIZE 이하면 hasMore=false를 반환한다.")
    void findMessageHistory_initialLoad_hasMoreFalseTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        for (int i = 0; i < 5; i++) {
            messageService.saveMessage(member.getId(), chatRoomId, "message" + i);
        }

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, member.getId(), null);

        // then
        assertThat(response.getMessages()).hasSize(5);
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("초기 진입 시 메시지는 오래된 순으로 반환된다.")
    void findMessageHistory_initialLoad_messagesInAscendingOrderTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = messageService.saveMessage(member.getId(), chatRoomId, "first");
        Long secondChatId = messageService.saveMessage(member.getId(), chatRoomId, "second");
        Long thirdChatId = messageService.saveMessage(member.getId(), chatRoomId, "third");

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, member.getId(), null);

        // then
        List<MessageHistory> messages = response.getMessages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getChatId()).isEqualTo(firstChatId);
        assertThat(messages.get(1).getChatId()).isEqualTo(secondChatId);
        assertThat(messages.get(2).getChatId()).isEqualTo(thirdChatId);
    }

    @Test
    @DisplayName("커서 기반 조회는 beforeChatId보다 오래된 메시지를 오래된 순으로 반환한다.")
    void findMessageHistory_cursorLoad_returnsMessagesBeforeCursorTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = messageService.saveMessage(member.getId(), chatRoomId, "first");
        Long secondChatId = messageService.saveMessage(member.getId(), chatRoomId, "second");
        Long thirdChatId = messageService.saveMessage(member.getId(), chatRoomId, "third");

        // when: thirdChatId를 커서로 → first, second만 반환
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, member.getId(), thirdChatId);

        // then
        List<MessageHistory> messages = response.getMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getChatId()).isEqualTo(firstChatId);
        assertThat(messages.get(1).getChatId()).isEqualTo(secondChatId);
    }

    @Test
    @DisplayName("커서 기반 조회 시 읽음 처리를 수행하지 않는다.")
    void findMessageHistory_cursorLoad_doesNotUpdateReadStatusTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        messageService.saveMessage(sender.getId(), chatRoomId, "first");
        messageService.saveMessage(sender.getId(), chatRoomId, "second");
        Long thirdChatId = messageService.saveMessage(sender.getId(), chatRoomId, "third");

        // when: receiver가 커서로 이전 메시지 조회
        messageService.findMessageHistory(chatRoomId, receiver.getId(), thirdChatId);

        // then: receiver의 미읽음 상태 그대로 (읽음 처리 안 됨)
        em.flush(); em.clear();
        Long receiverCursor = spaceMemberRepository
                .findLastReadMessageIdBy(receiver.getId(), chatRoomId);
        assertThat(receiverCursor).isNull();
    }

    @Test
    @DisplayName("커서 기반 조회 시 lastReadMessageId는 null을 반환한다.")
    void findMessageHistory_cursorLoad_lastReadMessageIdIsNullTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        messageService.saveMessage(member.getId(), chatRoomId, "first");
        Long secondChatId = messageService.saveMessage(member.getId(), chatRoomId, "second");

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, member.getId(), secondChatId);

        // then
        assertThat(response.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("커서 이전 메시지가 PAGE_SIZE를 초과하면 hasMore=true를 반환한다.")
    void findMessageHistory_cursorLoad_hasMoreTrueTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        // 32개 저장 후 마지막 id를 커서로 → 이전 31개 존재 → hasMore=true
        Long cursorChatId = null;
        for (int i = 0; i < 32; i++) {
            cursorChatId = messageService.saveMessage(member.getId(), chatRoomId, "message" + i);
        }

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, member.getId(), cursorChatId);

        // then
        assertThat(response.getMessages()).hasSize(30);
        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    @DisplayName("커서 이전 메시지가 없으면 빈 리스트와 hasMore=false를 반환한다.")
    void findMessageHistory_cursorLoad_noPreviousMessages_returnsEmptyTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = messageService.saveMessage(member.getId(), chatRoomId, "only message");

        // when: 첫 번째 메시지를 커서로 → 이전 메시지 없음
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, member.getId(), firstChatId);

        // then
        assertThat(response.getMessages()).isEmpty();
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("읽지 않은 메시지가 없으면 PublishReadEvent가 발행되지 않는다.")
    void findMessageHistory_noUnread_doesNotPublishReadEvent(ApplicationEvents events) {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");

        Space chatRoom = fixture.savedChatRoomBy("title", List.of(firstMember, secondMember));
        Long chatRoomId = chatRoom.getId();

        messageService.saveMessage(firstMember.getId(), chatRoomId, "message");

        // 첫 번째 입장: 미읽음 읽음 처리 → 이벤트 발행됨
        messageService.findMessageHistory(chatRoomId, secondMember.getId(), null);

        // when: 재입장 — 미읽음 없음
        messageService.findMessageHistory(chatRoomId, secondMember.getId(), null);

        // then: 두 번 호출했지만 이벤트는 첫 번째 호출에서만 1건 발행
        long eventCount = events.stream(PublishReadEvent.class).count();
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("메시지 전송 시 sender의 lastReadMessageId가 저장된 chatId로 갱신된다.")
    void saveChat_senderCursorUpdatedTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));

        // when
        Long savedChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), "hello");
        em.clear();

        // then
        SpaceMember senderParticipant = spaceMemberRepository
                .findChatRoomBy(chatRoom.getId(), sender.getId());
        assertThat(senderParticipant.getLastReadMessageId()).isEqualTo(savedChatId);
    }

    @Test
    @DisplayName("메시지 전송 시 receiver의 lastReadMessageId는 갱신되지 않는다.")
    void saveChat_receiverCursorNotUpdatedTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));

        // when
        messageService.saveMessage(sender.getId(), chatRoom.getId(), "hello");
        em.clear();

        // then
        SpaceMember receiverParticipant = spaceMemberRepository
                .findChatRoomBy(chatRoom.getId(), receiver.getId());
        assertThat(receiverParticipant.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("초기 history 조회 시 조회자의 lastReadMessageId가 최신 chatId로 갱신된다.")
    void findMessageHistory_initialLoad_cursorUpdatedTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(me, other));
        Long chatRoomId = chatRoom.getId();

        messageService.saveMessage(other.getId(), chatRoomId, "first");
        Long latestChatId = messageService.saveMessage(other.getId(), chatRoomId, "second");

        // when
        messageService.findMessageHistory(chatRoomId, me.getId(), null);
        em.clear();

        // then
        SpaceMember participant = spaceMemberRepository
                .findChatRoomBy(chatRoomId, me.getId());
        assertThat(participant.getLastReadMessageId()).isEqualTo(latestChatId);
    }

    @Test
    @DisplayName("페이지네이션 history 조회 시 lastReadMessageId는 갱신되지 않는다.")
    void findMessageHistory_pagination_cursorNotUpdatedTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(me, other));
        Long chatRoomId = chatRoom.getId();

        // other가 메시지 2개 전송 → me는 receiver, cursor 갱신 없음
        messageService.saveMessage(other.getId(), chatRoomId, "first");
        Long secondChatId = messageService.saveMessage(other.getId(), chatRoomId, "second");

        // 초기 조회로 me cursor를 secondChatId로 세팅
        messageService.findMessageHistory(chatRoomId, me.getId(), null);
        em.clear();

        // other가 세 번째 메시지 전송 → me cursor: secondChatId 유지 (receiver)
        Long thirdChatId = messageService.saveMessage(other.getId(), chatRoomId, "third");
        em.clear();

        // when: beforeChatId != null 페이지네이션 조회
        messageService.findMessageHistory(chatRoomId, me.getId(), thirdChatId);
        em.clear();

        // then: cursor는 secondChatId 그대로 (thirdChatId로 진행하지 않음)
        SpaceMember participant = spaceMemberRepository
                .findChatRoomBy(chatRoomId, me.getId());
        assertThat(participant.getLastReadMessageId()).isEqualTo(secondChatId);
    }

    @Test
    @DisplayName("빈 채팅방 조회 시 lastReadMessageId는 null 그대로다.")
    void findMessageHistory_emptyRoom_cursorNotUpdatedTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(me));

        // when
        messageService.findMessageHistory(chatRoom.getId(), me.getId(), null);
        em.clear();

        // then
        SpaceMember participant = spaceMemberRepository
                .findChatRoomBy(chatRoom.getId(), me.getId());
        assertThat(participant.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("메시지 전송 시 ROOM_ACTIVE 상태인 수신자라도 CHAT_MESSAGE만으로는 cursor가 갱신되지 않는다 (READ_UP_TO/ROOM_ACTIVE가 담당).")
    void saveChat_activeRoomReceiverCursorNotUpdatedTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member activeReceiver = fixture.savedMemberBy("activeReceiver");
        Member inactiveReceiver = fixture.savedMemberBy("inactiveReceiver");
        Space chatRoom = fixture.savedChatRoomBy("room",
                List.of(sender, activeReceiver, inactiveReceiver));

        // activeReceiver: 세션 등록 + 방 입장 (ENTER_ROOM으로 자동 active 설정)
        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getId()).willReturn("session-active");
        given(mockSession.getAttributes())
                .willReturn(Map.of(SessionConst.SESSION_ID, activeReceiver.getId()));
        spaceManager.registerSession(mockSession);
        spaceManager.addSessionToSpace(mockSession, chatRoom.getId());

        // when
        Long savedChatId = messageService.saveMessage(sender.getId(), chatRoom.getId(), "hello");
        em.clear();

        // then: sender → cursor 갱신됨 (sender-only 정책)
        SpaceMember senderParticipant = spaceMemberRepository
                .findChatRoomBy(chatRoom.getId(), sender.getId());
        assertThat(senderParticipant.getLastReadMessageId()).isEqualTo(savedChatId);

        // activeReceiver → CHAT_MESSAGE만으로는 cursor 갱신 안 됨 (READ_UP_TO/ROOM_ACTIVE가 담당)
        SpaceMember activeParticipant = spaceMemberRepository
                .findChatRoomBy(chatRoom.getId(), activeReceiver.getId());
        assertThat(activeParticipant.getLastReadMessageId()).isNull();

        // inactiveReceiver → cursor 갱신 안 됨
        SpaceMember inactiveParticipant = spaceMemberRepository
                .findChatRoomBy(chatRoom.getId(), inactiveReceiver.getId());
        assertThat(inactiveParticipant.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("메시지 전송 시 방에 접속 중이지만 inactive 상태인 수신자도 cursor가 갱신되지 않는다.")
    void saveChat_inRoomButInactiveReceiverCursorNotUpdatedTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member inRoomReceiver = fixture.savedMemberBy("inRoomReceiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, inRoomReceiver));

        // 방 입장(ENTER_ROOM) 후 즉시 ROOM_INACTIVE로 inactive 전환
        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getId()).willReturn("session-in-room");
        given(mockSession.getAttributes())
                .willReturn(Map.of(SessionConst.SESSION_ID, inRoomReceiver.getId()));
        spaceManager.registerSession(mockSession);
        spaceManager.addSessionToSpace(mockSession, chatRoom.getId());        // auto-activate
        spaceManager.deactivateSpace(mockSession.getId(), chatRoom.getId());  // ROOM_INACTIVE

        // when
        messageService.saveMessage(sender.getId(), chatRoom.getId(), "hello");
        em.clear();

        // then: cursor 갱신 없음
        SpaceMember participant = spaceMemberRepository
                .findChatRoomBy(chatRoom.getId(), inRoomReceiver.getId());
        assertThat(participant.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("수신자 첫 입장 시 lastReadMessageId는 null이다.")
    void findMessageHistory_firstEnter_lastReadMessageIdIsNull() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        messageService.saveMessage(sender.getId(), chatRoomId, "first");
        messageService.saveMessage(sender.getId(), chatRoomId, "second");

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId,
                receiver.getId(), null);

        // then
        assertThat(response.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("두 번째 입장 시 lastReadMessageId는 직전 입장에서 갱신된 cursor 값이다.")
    void findMessageHistory_secondEnter_lastReadMessageIdEqualsPreUpdateCursor() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member me = fixture.savedMemberBy("me");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, me));
        Long chatRoomId = chatRoom.getId();

        messageService.saveMessage(sender.getId(), chatRoomId, "first");
        Long secondChatId = messageService.saveMessage(sender.getId(), chatRoomId, "second");

        // 첫 입장: cursor가 secondChatId로 갱신됨
        messageService.findMessageHistory(chatRoomId, me.getId(), null);

        // 새 메시지 도착
        messageService.saveMessage(sender.getId(), chatRoomId, "third");

        // when: 두 번째 입장
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, me.getId(),
                null);

        // then: cursor 갱신 이전 값 = 첫 입장 때 갱신된 secondChatId
        assertThat(response.getLastReadMessageId()).isEqualTo(secondChatId);
    }

    @Test
    @DisplayName("발신자 재입장 시 lastReadMessageId는 자신이 마지막으로 보낸 메시지 ID이다.")
    void findMessageHistory_senderReenter_lastReadMessageIdEqualsSentMessageCursor() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        Long sentChatId = messageService.saveMessage(sender.getId(), chatRoomId, "hello");
        messageService.saveMessage(receiver.getId(), chatRoomId, "reply"); // sender cursor 변화 없음

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, sender.getId(),
                null);

        // then: saveChat에서 갱신된 cursor = sentChatId
        assertThat(response.getLastReadMessageId()).isEqualTo(sentChatId);
    }

    @Test
    @DisplayName("ROOM_ACTIVE 시 inactive 동안 쌓인 메시지를 최신 chatId까지 cursor가 갱신된다.")
    void onRoomActive_cursorAdvancedToLatestTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        // receiver: ENTER_ROOM(auto-activate) 후 ROOM_INACTIVE
        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getId()).willReturn("session-receiver");
        given(mockSession.getAttributes())
                .willReturn(Map.of(SessionConst.SESSION_ID, receiver.getId()));
        spaceManager.registerSession(mockSession);
        spaceManager.addSessionToSpace(mockSession, chatRoomId);
        spaceManager.deactivateSpace(mockSession.getId(), chatRoomId);

        // inactive 동안 메시지 5개 도착
        messageService.saveMessage(sender.getId(), chatRoomId, "msg1");
        messageService.saveMessage(sender.getId(), chatRoomId, "msg2");
        messageService.saveMessage(sender.getId(), chatRoomId, "msg3");
        messageService.saveMessage(sender.getId(), chatRoomId, "msg4");
        Long latestChatId = messageService.saveMessage(sender.getId(), chatRoomId, "msg5");

        // when: ROOM_ACTIVE
        messageService.onRoomActive(receiver.getId(), chatRoomId);
        em.flush(); em.clear();

        // then: cursor가 최신 메시지까지 갱신되어야 한다
        SpaceMember participant = spaceMemberRepository
                .findChatRoomBy(chatRoomId, receiver.getId());
        assertThat(participant.getLastReadMessageId()).isEqualTo(latestChatId);
    }

    @Test
    @DisplayName("ROOM_ACTIVE 시 방에 메시지가 없으면 예외 없이 return된다.")
    void onRoomActive_emptyRoom_returnsWithoutExceptionTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        // when & then: 예외 없이 정상 종료
        assertThatCode(() -> messageService.onRoomActive(member.getId(), chatRoom.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ROOM_ACTIVE 시 이미 최신 cursor이면 PublishReadEvent가 발행되지 않는다.")
    void onRoomActive_alreadyAtLatestCursor_doesNotPublishEventTest(ApplicationEvents events) {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        Long chatId = messageService.saveMessage(sender.getId(), chatRoomId, "msg");

        // READ_UP_TO로 이미 최신까지 읽음 처리된 상태 (sender-only 정책에서 active 상태의 실제 catch-up 경로)
        messageService.onReadUpTo(receiver.getId(), chatRoomId, chatId);

        // when: 이미 최신인 상태에서 ROOM_ACTIVE
        messageService.onRoomActive(receiver.getId(), chatRoomId);

        // then: onReadUpTo에서 발행된 이벤트 1건만 존재, ROOM_ACTIVE로 인한 추가 발행 없음
        long eventCount = events.stream(PublishReadEvent.class).count();
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("ROOM_ACTIVE 시 unread가 있으면 PublishReadEvent가 발행된다.")
    void onRoomActive_withUnread_publishesReadEventTest(ApplicationEvents events) {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        // receiver: ENTER_ROOM 후 ROOM_INACTIVE
        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getId()).willReturn("session-receiver");
        given(mockSession.getAttributes())
                .willReturn(Map.of(SessionConst.SESSION_ID, receiver.getId()));
        spaceManager.registerSession(mockSession);
        spaceManager.addSessionToSpace(mockSession, chatRoomId);
        spaceManager.deactivateSpace(mockSession.getId(), chatRoomId);

        // inactive 동안 메시지 3개
        Long firstChatId = messageService.saveMessage(sender.getId(), chatRoomId, "msg1");
        messageService.saveMessage(sender.getId(), chatRoomId, "msg2");
        Long latestChatId = messageService.saveMessage(sender.getId(), chatRoomId, "msg3");

        // receiver cursor 상태 확인 (inactive였으므로 null이어야 함)
        Long cursorBefore = spaceMemberRepository
                .findLastReadMessageIdBy(receiver.getId(), chatRoomId);
        assertThat(cursorBefore).isNull(); // inactive 중 메시지는 cursor advance 없음

        // when: ROOM_ACTIVE
        messageService.onRoomActive(receiver.getId(), chatRoomId);

        // then: PublishReadEvent 1건 발행
        List<PublishReadEvent> publishedEvents =
                events.stream(PublishReadEvent.class).toList();
        assertThat(publishedEvents).hasSize(1);

        PublishReadEvent event = publishedEvents.get(0);
        assertThat(event.getMemberId()).isEqualTo(receiver.getId());
        assertThat(event.getChatRoomId()).isEqualTo(chatRoomId);
        assertThat(event.getPreviousLastReadChatId()).isNull(); // 이전 cursor = null
    }

    @Test
    @DisplayName("다중 세션에서 ROOM_ACTIVE가 동시에 와도 PublishReadEvent는 1건만 발행된다.")
    void onRoomActive_duplicateCall_publishesEventOnceTest(ApplicationEvents events) {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        // inactive 동안 메시지 1개
        messageService.saveMessage(sender.getId(), chatRoomId, "msg");

        // when: 같은 memberId로 ROOM_ACTIVE 두 번 (다중 세션 시뮬레이션)
        messageService.onRoomActive(receiver.getId(), chatRoomId); // 1st → cursor advance, 이벤트발행
        messageService.onRoomActive(receiver.getId(), chatRoomId); // 2nd → cursor 이미 최신,이벤트 미발행

        // then: 이벤트는 1건만 발행
        long eventCount = events.stream(PublishReadEvent.class).count();
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("READ_UP_TO 요청 시 cursor가 요청한 messageId까지 전진한다.")
    void onReadUpTo_cursorAdvancesTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        Long chatId = messageService.saveMessage(sender.getId(), chatRoomId, "hello");

        // when: receiver가 READ_UP_TO(chatId)
        messageService.onReadUpTo(receiver.getId(), chatRoomId, chatId);
        em.flush(); em.clear();

        // then
        SpaceMember participant = spaceMemberRepository
                .findChatRoomBy(chatRoomId, receiver.getId());
        assertThat(participant.getLastReadMessageId()).isEqualTo(chatId);
    }

    @Test
    @DisplayName("READ_UP_TO 성공 시 PublishReadEvent가 발행되고 previous/current 값이 정확하다.")
    void onReadUpTo_publishesReadEventWithCorrectCursorsTest(ApplicationEvents events) {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        Long chatId = messageService.saveMessage(sender.getId(), chatRoomId, "hello");

        // when
        messageService.onReadUpTo(receiver.getId(), chatRoomId, chatId);

        // then
        List<PublishReadEvent> publishedEvents = events.stream(PublishReadEvent.class).toList();
        assertThat(publishedEvents).hasSize(1);

        PublishReadEvent event = publishedEvents.get(0);
        assertThat(event.getMemberId()).isEqualTo(receiver.getId());
        assertThat(event.getChatRoomId()).isEqualTo(chatRoomId);
        assertThat(event.getPreviousLastReadChatId()).isNull(); // 이전에 읽은 기록 없음
        assertThat(event.getCurrentLastReadChatId()).isEqualTo(chatId);
    }

    @Test
    @DisplayName("이미 읽은 messageId로 READ_UP_TO를 보내면 갱신되지 않고 이벤트도 발행되지 않는다.")
    void onReadUpTo_alreadyReadNoop_doesNotPublishEventTest(ApplicationEvents events) {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = messageService.saveMessage(sender.getId(), chatRoomId, "first");
        Long secondChatId = messageService.saveMessage(sender.getId(), chatRoomId, "second");

        // 먼저 secondChatId까지 읽음 처리
        messageService.onReadUpTo(receiver.getId(), chatRoomId, secondChatId);

        // when: 이미 읽은 firstChatId(더 과거)로 다시 READ_UP_TO
        assertThatCode(() -> messageService.onReadUpTo(receiver.getId(), chatRoomId, firstChatId))
                .doesNotThrowAnyException();
        em.flush(); em.clear();

        // then: cursor는 secondChatId 그대로, 두 번째 호출로는 이벤트가 추가 발행되지 않음
        SpaceMember participant = spaceMemberRepository
                .findChatRoomBy(chatRoomId, receiver.getId());
        assertThat(participant.getLastReadMessageId()).isEqualTo(secondChatId);
        assertThat(events.stream(PublishReadEvent.class).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 room의 messageId로 READ_UP_TO를 보내면 MESSAGE_NOT_FOUND로 거부된다.")
    void onReadUpTo_otherRoomMessageId_throwsMessageNotFoundTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Space otherRoom = fixture.savedChatRoomBy("otherRoom", List.of(member));

        Long otherRoomChatId = messageService.saveMessage(member.getId(), otherRoom.getId(), "hello");

        // when & then
        assertThatThrownBy(() ->
                messageService.onReadUpTo(member.getId(), chatRoom.getId(), otherRoomChatId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 messageId로 READ_UP_TO를 보내면 MESSAGE_NOT_FOUND로 거부된다.")
    void onReadUpTo_nonExistentMessageId_throwsMessageNotFoundTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        // when & then
        assertThatThrownBy(() ->
                messageService.onReadUpTo(member.getId(), chatRoom.getId(), 999_999_999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("null 또는 음수 messageId로 READ_UP_TO를 보내면 MESSAGE_NOT_FOUND로 거부된다.")
    void onReadUpTo_nullOrNegativeMessageId_throwsMessageNotFoundTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        // when & then
        assertThatThrownBy(() ->
                messageService.onReadUpTo(member.getId(), chatRoom.getId(), null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);

        assertThatThrownBy(() ->
                messageService.onReadUpTo(member.getId(), chatRoom.getId(), -1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("Discussion 없는 메시지는 discussionId=null, discussionMessageCount=0을 반환한다.")
    void findMessageHistory_messageWithoutDiscussion_discussionFieldsAreDefault() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        messageService.saveMessage(member.getId(), chatRoom.getId(), "hello");

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoom.getId(), member.getId(), null);

        // then
        assertThat(response.getMessages()).hasSize(1);
        MessageHistory msg = response.getMessages().get(0);
        assertThat(msg.getDiscussionId()).isNull();
        assertThat(msg.getDiscussionMessageCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Discussion 있는 메시지는 discussionId와 count가 정확히 내려온다.")
    void findMessageHistory_messageWithDiscussion_discussionIdAndCountReturned() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        Long chatId = messageService.saveMessage(member.getId(), chatRoom.getId(), "root message");
        Message rootMessage = messageRepository.findById(chatId).get();
        Discussion discussion = discussionRepository.save(Discussion.of(rootMessage));
        discussionMessageRepository.save(DiscussionMessage.of("reply1", discussion, member));
        discussionMessageRepository.save(DiscussionMessage.of("reply2", discussion, member));
        discussionMessageRepository.save(DiscussionMessage.of("reply3", discussion, member));

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoom.getId(), member.getId(), null);

        // then
        assertThat(response.getMessages()).hasSize(1);
        MessageHistory msg = response.getMessages().get(0);
        assertThat(msg.getDiscussionId()).isEqualTo(discussion.getId());
        assertThat(msg.getDiscussionMessageCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Discussion 있는 메시지와 없는 메시지가 섞여도 message별 discussion 정보가 정확히 매핑된다.")
    void findMessageHistory_mixedMessages_discussionSummaryMappedPerMessage() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        // msg1: Discussion 있음 (reply 2개)
        Long chatId1 = messageService.saveMessage(member.getId(), chatRoomId, "msg1");
        Message rootMessage1 = messageRepository.findById(chatId1).get();
        Discussion discussion1 = discussionRepository.save(Discussion.of(rootMessage1));
        discussionMessageRepository.save(DiscussionMessage.of("reply1", discussion1, member));
        discussionMessageRepository.save(DiscussionMessage.of("reply2", discussion1, member));

        // msg2: Discussion 없음
        messageService.saveMessage(member.getId(), chatRoomId, "msg2");

        // msg3: Discussion 있음 (reply 5개)
        Long chatId3 = messageService.saveMessage(member.getId(), chatRoomId, "msg3");
        Message rootMessage3 = messageRepository.findById(chatId3).get();
        Discussion discussion3 = discussionRepository.save(Discussion.of(rootMessage3));
        for (int i = 1; i <= 5; i++) {
            discussionMessageRepository.save(DiscussionMessage.of("reply" + i, discussion3, member));
        }

        // when
        MessageHistoryResponse response = messageService.findMessageHistory(chatRoomId, member.getId(), null);

        // then: 오름차순 반환(가장 오래된 msg1이 index 0)
        List<MessageHistory> messages = response.getMessages();
        assertThat(messages).hasSize(3);

        MessageHistory msg1 = messages.get(0);
        assertThat(msg1.getDiscussionId()).isEqualTo(discussion1.getId());
        assertThat(msg1.getDiscussionMessageCount()).isEqualTo(2L);

        MessageHistory msg2 = messages.get(1);
        assertThat(msg2.getDiscussionId()).isNull();
        assertThat(msg2.getDiscussionMessageCount()).isEqualTo(0L);

        MessageHistory msg3 = messages.get(2);
        assertThat(msg3.getDiscussionId()).isEqualTo(discussion3.getId());
        assertThat(msg3.getDiscussionMessageCount()).isEqualTo(5L);
    }
}