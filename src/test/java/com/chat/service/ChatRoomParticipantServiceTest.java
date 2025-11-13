package com.chat.service;

import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import com.chat.repository.ChatRoomParticipantRepository;
import com.chat.repository.ChatRoomRepository;
import com.chat.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatRoomParticipantServiceTest {

    @Autowired
    private ChatRoomParticipantService chatRoomParticipantService;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Test
    @DisplayName("사용자가 채팅방에 입장한다.")
    void enterChatRoomTest() {
        // given
        Member member = createMember("member");
        ChatRoom chatRoom = createChatRoom("title");
        ChatRoomParticipant chatRoomParticipant =
                new ChatRoomParticipant(false, member, chatRoom);
        chatRoomParticipantRepository.save(chatRoomParticipant);

        // when
        chatRoomParticipantService.enterChatRoom(chatRoom.getId(), member.getId());

        // then
        assertThat(chatRoomParticipant.isParticipate()).isTrue();
    }

    @Test
    @DisplayName("사용자가 채팅방에서 퇴장한다.")
    void leaveChatRoomTest() {
        // given
        Member member = createMember("member");
        ChatRoom chatRoom = createChatRoom("title");
        ChatRoomParticipant chatRoomParticipant =
                new ChatRoomParticipant(true, member, chatRoom);
        chatRoomParticipantRepository.save(chatRoomParticipant);

        // when
        chatRoomParticipantService.leaveChatRoom(chatRoom.getId(), member.getId());

        // then
        assertThat(chatRoomParticipant.isParticipate()).isFalse();
    }

    private Member createMember(String username) {
        String commonPassword = "password";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }

    private ChatRoom createChatRoom(String title) {
        ChatRoom chatRoom = ChatRoom.of(title);
        return chatRoomRepository.save(chatRoom);
    }
}