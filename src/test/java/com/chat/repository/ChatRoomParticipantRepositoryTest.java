package com.chat.repository;

import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatRoomParticipantRepositoryTest {

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("채팅방 참여 정보를 저장한다.")
    void saveTest() {
        // given
        String username = "username";
        Member savedMember = createMemberBy(username);

        String title = "title";
        ChatRoom chatRoom = ChatRoom.of(title);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        ChatRoomParticipant chatRoomParticipant = new ChatRoomParticipant(true, savedMember, savedChatRoom);

        // when
        ChatRoomParticipant savedChatRoomParticipant = chatRoomParticipantRepository.save(chatRoomParticipant);

        // then
        assertThat(savedChatRoomParticipant.getId()).isNotNull();
        assertThat(savedChatRoomParticipant.getMember()).isEqualTo(savedMember);
        assertThat(savedChatRoomParticipant.getChatRoom()).isEqualTo(savedChatRoom);
    }

    @Test
    @DisplayName("사용자 ID 들로 구성된 채팅방이 존재하는지 확인한다.")
    void countByExactMembersTest() {
        // given
        List<Long> memberIds = new ArrayList<>();
        String firstUsername = "first";
        Member firstMember = createMemberBy(firstUsername);
        memberIds.add(firstMember.getId());

        String secondUsername = "second";
        Member secondMember = createMemberBy(secondUsername);
        memberIds.add(secondMember.getId());

        String thirdUsername = "third";
        Member thirdMember = createMemberBy(thirdUsername);
        memberIds.add(thirdMember.getId());

        String title = "title";
        ChatRoom chatRoom = createChatRoomBy(title);

        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, firstMember, chatRoom));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, secondMember, chatRoom));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(false, thirdMember, chatRoom));

        // when
        List<Long> chatRoomIds = chatRoomParticipantRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

        // then
        assertThat(chatRoomIds).hasSize(1);
    }

    @Test
    @DisplayName("일부 사용자만 포함된 경우 채팅방이 조회되지 않는다.")
    void partialMemberChatRoomTest() {
        // given
        List<Long> memberIds = new ArrayList<>();
        String firstUsername = "first";
        Member firstMember = createMemberBy(firstUsername);
        memberIds.add(firstMember.getId());

        String secondUsername = "second";
        Member secondMember = createMemberBy(secondUsername);
        memberIds.add(secondMember.getId());

        String thirdUsername = "third";
        Member thirdMember = createMemberBy(thirdUsername);

        String title = "title";
        ChatRoom chatRoom = createChatRoomBy(title);

        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, firstMember, chatRoom));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, secondMember, chatRoom));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(false, thirdMember, chatRoom));

        // when
        List<Long> chatRoomIds = chatRoomParticipantRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

        // then
        assertThat(chatRoomIds).hasSize(0);
    }

    @Test
    @DisplayName("채팅방 사용자보다 많은 ID 가 포함된 경우 채팅방이 조회되지 않는다.")
    void memberChatRoomTest() {
        // given
        List<Long> memberIds = new ArrayList<>();
        String firstUsername = "first";
        Member firstMember = createMemberBy(firstUsername);
        memberIds.add(firstMember.getId());

        String secondUsername = "second";
        Member secondMember = createMemberBy(secondUsername);
        memberIds.add(secondMember.getId());

        String thirdUsername = "third";
        Member thirdMember = createMemberBy(thirdUsername);
        memberIds.add(thirdMember.getId());

        String title = "title";
        ChatRoom chatRoom = createChatRoomBy(title);

        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, firstMember, chatRoom));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, secondMember, chatRoom));

        // when
        List<Long> chatRoomIds = chatRoomParticipantRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

        // then
        assertThat(chatRoomIds).hasSize(0);
    }

    @Test
    @DisplayName("사용자 ID 로 채팅방 참여 정보를 조회한다.")
    void findAllByMemberIdTest() {
        // given
        String username = "username";
        Member member = createMemberBy(username);

        String firstTitle = "first";
        ChatRoom first = createChatRoomBy(firstTitle);
        String secondTitle = "secondTitle";
        ChatRoom second = createChatRoomBy(secondTitle);

        ChatRoomParticipant firstParticipant = chatRoomParticipantRepository.save(new ChatRoomParticipant(true, member, first));
        ChatRoomParticipant secondParticipant = chatRoomParticipantRepository.save(new ChatRoomParticipant(true, member, second));

        // when
        List<ChatRoomParticipant> findChatRoomParticipants = chatRoomParticipantRepository.findAllBy(member.getId());

        // then
        assertThat(findChatRoomParticipants).hasSize(2);
        assertThat(findChatRoomParticipants).containsExactly(firstParticipant, secondParticipant);
    }

    @Test
    @DisplayName("채팅방 ID 를 이용해 채팅방 참여, 회원 정보를 조회한다.")
    void findAllFetchMemberByTest() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        ChatRoom chatRoom = createChatRoomBy(title);
        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, member, chatRoom));

        // when
        List<ChatRoomParticipant> chatRoomParticipants
                = chatRoomParticipantRepository.findAllFetchMemberBy(chatRoom.getId());

        // then
        assertThat(chatRoomParticipants).hasSize(1);
    }

    @Test
    @DisplayName("채팅방 참여 조회 시 사용자 정보를 fetch 해 쿼리가 1번만 실행된다.")
    void shouldFetchMembersWithParticipantsUsingSingleQuery() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        ChatRoom chatRoom = createChatRoomBy(title);
        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, member, chatRoom));

        em.flush();
        em.clear();

        Session session = em.unwrap(Session.class);
        session.getSessionFactory().getStatistics().setStatisticsEnabled(true);
        session.getSessionFactory().getStatistics().clear();

        // when
        List<ChatRoomParticipant> chatRoomParticipants
                = chatRoomParticipantRepository.findAllFetchMemberBy(chatRoom.getId());
        for (ChatRoomParticipant chatRoomParticipant : chatRoomParticipants) {
            chatRoomParticipant.getMember().getUsername();
        }
        long queryCount = session.getSessionFactory().getStatistics().getPrepareStatementCount();

        // then
        assertThat(queryCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("채팅방 ID 와 사용자 ID 를 이용해 채팅방 참여 데이터를 조회한다.")
    void findChatRoomByChatRoomIdAndMemberIdTest() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        ChatRoom chatRoom = createChatRoomBy(title);
        chatRoomParticipantRepository.save(new ChatRoomParticipant(true, member, chatRoom));

        // when
        ChatRoomParticipant chatRoomParticipant = chatRoomParticipantRepository.findChatRoomBy(chatRoom.getId(), member.getId());

        // then
        assertThat(chatRoomParticipant.getChatRoom()).isEqualTo(chatRoom);
        assertThat(chatRoomParticipant.getMember()).isEqualTo(member);
    }

    private Member createMemberBy(String username) {
        String commonPassword = "commonPassword";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }

    private ChatRoom createChatRoomBy(String title) {
        ChatRoom chatRoom = ChatRoom.of(title);
        return chatRoomRepository.save(chatRoom);
    }
}