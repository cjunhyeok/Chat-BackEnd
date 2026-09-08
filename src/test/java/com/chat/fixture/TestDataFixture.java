package com.chat.fixture;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.SpaceMember;
import com.chat.entity.Member;
import com.chat.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.chat.fixture.ClientMessageIdFixture.nextClientMessageId;

@Component
@RequiredArgsConstructor
public class TestDataFixture {

    private static final String PASSWORD = "password";
    private static final String NICKNAME = "nickname";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private SpaceMemberRepository spaceMemberRepository;
    @Autowired
    private MessageRepository messageRepository;
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

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Space savedChatRoomBy(String title, List<Member> participants) {

        Space chatRoom = Space.of(title);
        Space savedChatRoom = spaceRepository.save(chatRoom);

        for (Member participant : participants) {
            SpaceMember spaceMember = SpaceMember.of(participant, savedChatRoom);
            spaceMemberRepository.save(spaceMember);
        }

        return savedChatRoom;
    }

    public Space savedSimpleChatRoom(String title) {
        Space chatRoom = Space.of(title);
        return spaceRepository.save(chatRoom);
    }

    public Message savedSimpleChat(String message, Member member, Space chatRoom) {
        Message chat = Message.of(message, member, chatRoom, nextClientMessageId());
        return messageRepository.save(chat);
    }

    @Transactional
    public void deleteAllData() {
        em.createQuery("DELETE FROM DiscussionMessage").executeUpdate();
        em.createQuery("DELETE FROM Discussion").executeUpdate();
        em.createQuery("DELETE FROM Message").executeUpdate();
        em.createQuery("DELETE FROM SpaceMember").executeUpdate();
        em.createQuery("DELETE FROM Space").executeUpdate();
        em.createQuery("DELETE FROM Member").executeUpdate();
        em.flush();
    }

}
