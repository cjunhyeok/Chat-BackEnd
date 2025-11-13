package com.chat.service;

import com.chat.entity.*;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.*;
import com.chat.service.dtos.ChatHistory;
import com.chat.service.dtos.SaveChatData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
class ChatServiceTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatReadRepository chatReadRepository;
    @Autowired
    private TestDataFixture fixture;

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

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);

        String message = "message";

        // when
        Long savedChatId = chatService.saveChat(sender.getId(), chatRoom.getId(), message);

        // then
        Chat chat = chatRepository.findById(savedChatId).get();
        assertThat(chat.getMessage()).isEqualTo(message);
        assertThat(chat.getChatRoom()).isEqualTo(chatRoom);
        assertThat(chat.getMember()).isEqualTo(sender);

        ChatRead chatReadSender = chatReadRepository.findBy(savedChatId, sender.getId());
        assertThat(chatReadSender.getIsRead()).isTrue();

        ChatRead chatReadReceiver1 = chatReadRepository.findBy(savedChatId, receiver1.getId());
        assertThat(chatReadReceiver1.getIsRead()).isFalse();

        ChatRead chatReadReceiver2 = chatReadRepository.findBy(savedChatId, receiver2.getId());
        assertThat(chatReadReceiver2.getIsRead()).isFalse();
    }

    @Test
    @DisplayName("특정 채팅에 대한 상세정보를 조회한다.")
    void findChatDataTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver1 = fixture.savedMemberBy("receiver1");
        Member receiver2 = fixture.savedMemberBy("receiver2");

        List<Member> participants = new ArrayList<>();
        participants.add(sender);
        participants.add(receiver1);
        participants.add(receiver2);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);

        String message = "message";

        Long savedChatId = chatService.saveChat(sender.getId(), chatRoom.getId(), message);

        // when
        SaveChatData chatData = chatService.findChatData(savedChatId);

        // then
        assertThat(chatData.getChatId()).isEqualTo(savedChatId);
        assertThat(chatData.getUnReadCount()).isEqualTo(2);
        assertThat(chatData.getCreateDate()).isNotNull();
    }

    @Test
    @DisplayName("채팅방의 채팅목록을 조회한다.")
    void findChatHistoryTest() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");
        Member thirdMember = fixture.savedMemberBy("thirdMember");

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);
        participants.add(thirdMember);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = chatService.saveChat(firstMember.getId(), chatRoomId, "message");
        chatService.saveChat(secondMember.getId(), chatRoomId, "secondMessage");
        chatService.saveChat(secondMember.getId(), chatRoomId, "thirdMessage");

        // when
        List<ChatHistory> chatHistory = chatService.findChatHistory(chatRoomId, firstMember.getId());

        // then
        assertThat(chatHistory).hasSize(3);
        ChatHistory firstChat = chatHistory.get(0);
        assertThat(firstChat.getChatId()).isEqualTo(firstChatId);
        assertThat(firstChat.getSenderId()).isEqualTo(firstMember.getId());
        assertThat(firstChat.getUnReadCount()).isEqualTo(1L);
        assertThat(firstChat.getMessage()).isEqualTo("message");
    }
}