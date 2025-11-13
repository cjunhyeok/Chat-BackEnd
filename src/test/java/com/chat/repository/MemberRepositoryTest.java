package com.chat.repository;

import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class MemberRepositoryTest {
    
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    
    @Test
    @DisplayName("회원 정보를 저장한다.")
    void saveTest() {
        // given
        String username = "username";
        String password = "password";
        String nickname = "nickname";
        Member member = Member.of(username, password, nickname);

        // when
        Member savedMember = memberRepository.save(member);

        // then
        assertThat(savedMember.getId()).isNotNull();
        assertThat(savedMember.getUsername()).isEqualTo(username);
        assertThat(savedMember.getPassword()).isEqualTo(password);
        assertThat(savedMember.getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("사용자 ID 가 존재하는지 조회한다.")
    void existsByUsernameTest() {
        // given
        String existUsername = "existUsername";
        Member member = createMemberBy(existUsername);

        // when
        boolean isExist = memberRepository.existsByUsername(existUsername);

        // then
        assertThat(isExist).isTrue();
    }

    @Test
    @DisplayName("사용자 ID 를 이용해 사용자 정보를 조회한다.")
    void findByUsernameTest() {
        // given
        String username = "username";
        Member member = createMemberBy(username);

        // when
        Optional<Member> findMemberOptional = memberRepository.findByUsername(username);

        // then
        assertThat(findMemberOptional).isNotEmpty();
        assertThat(findMemberOptional.get()).isEqualTo(member);
        assertThat(findMemberOptional.get().getId()).isNotNull();
    }

    // todo remove session test

    @Test
    @DisplayName("채팅방 ID 를 이용해 참여한 사용자 ID 를 조회한다.")
    void findMemberIdsInTest() {
        // given
        String firstUser = "first";
        Member firstMember = createMemberBy(firstUser);
        String secondUser = "second";
        Member secondMember = createMemberBy(secondUser);
        String thirdUser = "third";
        Member thirdMember = createMemberBy(thirdUser);

        // when
        String title = "title";
        ChatRoom chatRoom = ChatRoom.of(title);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        ChatRoomParticipant firstrChatRoomParticipant = new ChatRoomParticipant(true, firstMember, savedChatRoom);
        chatRoomParticipantRepository.save(firstrChatRoomParticipant);
        ChatRoomParticipant secondChatRoomParticipant = new ChatRoomParticipant(true, secondMember, savedChatRoom);
        chatRoomParticipantRepository.save(secondChatRoomParticipant);
        ChatRoomParticipant thirdChatRoomParticipant = new ChatRoomParticipant(false, thirdMember, savedChatRoom);
        chatRoomParticipantRepository.save(thirdChatRoomParticipant);

        // when
        List<Long> memberIds = memberRepository.findMemberIdsIn(chatRoom.getId());

        // then
        assertThat(memberIds).hasSize(3);
    }

    private Member createMemberBy(String username) {
        String commonPassword = "password";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }
}