package com.chat.service;

import com.chat.entity.Chat;
import com.chat.entity.ChatRead;
import com.chat.entity.ChatRoom;
import com.chat.entity.Member;
import com.chat.repository.ChatReadRepository;
import com.chat.repository.ChatRepository;
import com.chat.repository.ChatRoomRepository;
import com.chat.repository.MemberRepository;
import com.chat.service.dtos.LastChatRead;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatReadServiceTest {

    @Autowired
    private ChatReadService chatReadService;
    @Autowired
    private ChatReadRepository chatReadRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("채팅방에 회원별 마지막 읽은 메시지를 조회한다.")
    void findMembersChatReadInTest() {
        // given
        Member firstMember = createMember("firstUser");
        Member secondMember = createMember("secondUser");

        ChatRoom chatRoom = createChatRoom("title");

        Chat firstChat = createChat("message", firstMember, chatRoom);
        Chat secondChat = createChat("message", secondMember, chatRoom);
        Chat thirdChat = createChat("message", firstMember, chatRoom);

        chatReadRepository.save(new ChatRead(true, firstMember, firstChat));
        chatReadRepository.save(new ChatRead(true, firstMember, secondChat));
        chatReadRepository.save(new ChatRead(true, firstMember, thirdChat));

        chatReadRepository.save(new ChatRead(true, secondMember, firstChat));
        chatReadRepository.save(new ChatRead(true, secondMember, secondChat));
        chatReadRepository.save(new ChatRead(false, secondMember, thirdChat));

        // when
        LastChatRead lastChatRead =
                chatReadService.findLastChatBy(firstMember.getId(), chatRoom.getId());

        // then
        assertThat(lastChatRead.getMemberId()).isEqualTo(firstMember.getId());
        assertThat(lastChatRead.getLastChatReadId()).isEqualTo(thirdChat.getId());
    }

    @Test
    @DisplayName("사용자가 채팅방에서 마지막으로 읽은 채팅 ID 를 조회한다.")
    void findLastChatByTest() {
        // given
        Member firstMember = createMember("firstUser");
        Member secondMember = createMember("secondUser");

        ChatRoom chatRoom = createChatRoom("title");

        Chat firstChat = createChat("message1", firstMember, chatRoom);
        Chat secondChat = createChat("message2", secondMember, chatRoom);
        Chat thirdChat = createChat("message3", firstMember, chatRoom);

        // 첫 번째 회원의 읽음 상태 저장
        chatReadRepository.save(new ChatRead(true, firstMember, firstChat));
        chatReadRepository.save(new ChatRead(true, secondMember, secondChat));
        chatReadRepository.save(new ChatRead(true, firstMember, thirdChat));

        // when
        LastChatRead firstMemberLastRead =
                chatReadService.findLastChatBy(firstMember.getId(), chatRoom.getId());

        // then
        assertThat(firstMemberLastRead).isNotNull();
        assertThat(firstMemberLastRead.getMemberId()).isEqualTo(firstMember.getId());
        assertThat(firstMemberLastRead.getLastChatReadId()).isEqualTo(thirdChat.getId());
    }

    @Test
    @DisplayName("채팅방에 사용자가 읽은 채팅이 없을 경우 null 을 반환한다.")
    void findLastChatNullTest() {
        Member firstMember = createMember("firstUser");
        Member secondMember = createMember("secondUser");

        ChatRoom chatRoom = createChatRoom("title");

        Chat firstChat = createChat("message1", firstMember, chatRoom);
        Chat secondChat = createChat("message2", firstMember, chatRoom);
        Chat thirdChat = createChat("message3", firstMember, chatRoom);

        chatReadRepository.save(new ChatRead(true, firstMember, firstChat));
        chatReadRepository.save(new ChatRead(true, firstMember, secondChat));
        chatReadRepository.save(new ChatRead(true, firstMember, thirdChat));

        // when
        LastChatRead secondMemberLastRead =
                chatReadService.findLastChatBy(secondMember.getId(), chatRoom.getId());

        // then
        assertThat(secondMemberLastRead).isNull();
    }

    private Member createMember(String username) {
        String commonPassword = "password";
        String commonNickname = "nickname";
        Member member = Member.of(username, commonPassword, commonNickname);

        return memberRepository.save(member);
    }

    private ChatRoom createChatRoom(String title) {
        ChatRoom chatRoom = ChatRoom.of(title);
        return chatRoomRepository.save(chatRoom);
    }

    private Chat createChat(String message, Member member, ChatRoom chatRoom) {
        Chat chat = new Chat(message, member, chatRoom);
        return chatRepository.save(chat);
    }
}