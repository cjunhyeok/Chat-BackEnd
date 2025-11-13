package com.chat.fixture;

import com.chat.entity.Chat;
import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import com.chat.repository.ChatRepository;
import com.chat.repository.ChatRoomParticipantRepository;
import com.chat.repository.ChatRoomRepository;
import com.chat.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TestDataFixture {

    private static final String PASSWORD = "password";
    private static final String NICKNAME = "nickname";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRepository chatRepository;
    @PersistenceContext
    private EntityManager em;

    public Member savedMemberBy(String username) {
        Member member = Member.of(
                username,
                PASSWORD,
                NICKNAME
        );
        return memberRepository.save(member);
    }

    public ChatRoom savedChatRoomBy(String title, List<Member> participants) {

        ChatRoom chatRoom = ChatRoom.of(title);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        for (Member participant : participants) {
            ChatRoomParticipant chatRoomParticipant = ChatRoomParticipant.builder()
                    .chatRoom(savedChatRoom)
                    .member(participant)
                    .isParticipate(false)
                    .build();
            chatRoomParticipantRepository.save(chatRoomParticipant);
        }

        return savedChatRoom;
    }

    public ChatRoom savedSimpleChatRoom(String title) {
        ChatRoom chatRoom = ChatRoom.of(title);
        return chatRoomRepository.save(chatRoom);
    }

    public Chat savedSimpleChat(String message, Member member, ChatRoom chatRoom) {
        Chat chat = new Chat(message, member, chatRoom);
        return chatRepository.save(chat);
    }

    public void flushAllData() {
        em.flush();
        em.clear();
    }
}
