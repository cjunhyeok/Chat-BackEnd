package com.chat.repository;

import com.chat.entity.Chat;
import com.chat.entity.ChatRead;
import com.chat.entity.ChatRoom;
import com.chat.entity.Member;
import com.chat.fixture.TestDataFixture;
import com.chat.service.dtos.LastChatRead;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatReadRepositoryTest {

    @Autowired
    private ChatReadRepository chatReadRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private TestDataFixture fixture;

    @Test
    @DisplayName("채팅읽음 정보를 저장한다.")
    void saveTest() {
        // given
        String username = "username";
        Member savedMember = createMember(username);

        String title = "title";
        ChatRoom savedChatRoom = createChatRoom(title);

        String message = "message";
        Chat savedChat = createChat(message, savedMember, savedChatRoom);

        boolean isRead = true;
        ChatRead chatRead = new ChatRead(isRead, savedMember, savedChat);

        // when
        ChatRead savedChatRead = chatReadRepository.save(chatRead);

        // then
        assertThat(savedChatRead.getIsRead()).isTrue();
        assertThat(savedChatRead.getId()).isNotNull();
        assertThat(savedChatRead.getMember()).isEqualTo(savedMember);
        assertThat(savedChatRead.getChat()).isEqualTo(savedChat);
    }
    
    @Test
    @DisplayName("채팅방에 특정 회원이 읽지 않은 메시지 개수를 조회한다.")
    void findUnReadCountByChatRoomIdAndMemberIdTest() {
        String firstUsername = "firstUsername";
        Member firstMember = createMember(firstUsername);
        String secondUsername = "secondUsername";
        Member secondMember = createMember(secondUsername);
        String thirdUsername = "thirdUsername";
        Member thirdMember = createMember(thirdUsername);

        String title = "title";
        ChatRoom chatRoom = createChatRoom(title);

        String message = "message";
        Chat firstChat = createChat(message, secondMember, chatRoom);
        Chat secondChat = createChat(message, thirdMember, chatRoom);

        boolean isRead = false;
        ChatRead firsdChatRead = new ChatRead(isRead, firstMember, firstChat);
        chatReadRepository.save(firsdChatRead);
        ChatRead secondChatRead = new ChatRead(isRead, firstMember, secondChat);
        chatReadRepository.save(secondChatRead);

        isRead = true;
        ChatRead thirdMemberRead = new ChatRead(isRead, thirdMember, firstChat);
        chatReadRepository.save(thirdMemberRead);

        // when
        Long unReadCount = chatReadRepository.findUnReadCountBy(chatRoom.getId(), firstMember.getId());

        // then
        assertThat(unReadCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("특정 채팅을 읽지 않은 사용자 수를 조회한다.")
    void findUnReadCountByChatIdTest() {
        // given
        String firstUsername = "firstUsername";
        Member firstMember = createMember(firstUsername);
        String secondUsername = "secondUsername";
        Member secondMember = createMember(secondUsername);
        String thirdUsername = "thirdUsername";
        Member thirdMember = createMember(thirdUsername);

        String title = "title";
        ChatRoom chatRoom = createChatRoom(title);

        String message = "message";
        Chat chat = createChat(message, firstMember, chatRoom);

        boolean isRead = true;
        ChatRead firstMemberChatRead = new ChatRead(isRead, firstMember, chat);
        chatReadRepository.save(firstMemberChatRead);

        isRead = false;
        ChatRead secondMemberChatRead = new ChatRead(isRead, secondMember, chat);
        chatReadRepository.save(secondMemberChatRead);
        ChatRead thirdMemberChatRead = new ChatRead(isRead, thirdMember, chat);
        chatReadRepository.save(thirdMemberChatRead);

        // when
        Long unReadCount = chatReadRepository.findUnReadCountBy(chat.getId());

        // then
        assertThat(unReadCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("채팅 ID 와 사용자 ID 를 이용해 채팅읽음 정보를 조회한다.")
    void findByChatIdAndMemberIdTest() {
        // given
        String username = "username";
        Member member = createMember(username);

        String title = "title";
        ChatRoom chatRoom = createChatRoom(title);

        String message = "message";
        Chat chat = createChat(message, member, chatRoom);

        boolean isRead = true;
        ChatRead chatRead = new ChatRead(isRead, member, chat);
        ChatRead savedChatRead = chatReadRepository.save(chatRead);

        // when
        ChatRead findChatRead
                = chatReadRepository.findBy(chat.getId(), member.getId());

        // then
        assertThat(findChatRead).isEqualTo(savedChatRead);
    }

    @Test
    @DisplayName("채팅방의 회원별 마지막으로 읽은 메시지 ID 를 조회한다.")
    void findLastReadChatsByChatRoomIdTest() {
        // given
        String firstUsername = "firstUsername";
        Member firstMember = createMember(firstUsername);
        String secondUsername = "secondUsername";
        Member secondMember = createMember(secondUsername);
        String thirdUsername = "thirdUsername";
        Member thirdMember = createMember(thirdUsername);

        String title = "title";
        ChatRoom chatRoom = createChatRoom(title);

        String message = "message";
        Chat firstChat = createChat(message, firstMember, chatRoom);
        boolean isRead = true;
        ChatRead firstChatFirstMember = new ChatRead(isRead, firstMember, firstChat);
        chatReadRepository.save(firstChatFirstMember);
        ChatRead firstChatSecondMember = new ChatRead(isRead, secondMember, firstChat);
        chatReadRepository.save(firstChatSecondMember);
        isRead = false;
        ChatRead firstChatThirdMember = new ChatRead(isRead, thirdMember, firstChat);
        chatReadRepository.save(firstChatThirdMember);

        Chat secondChat = createChat(message, secondMember, chatRoom);
        ChatRead secondChatFirstMember = new ChatRead(isRead, firstMember, secondChat);
        chatReadRepository.save(secondChatFirstMember);
        ChatRead secondChatThirdMember = new ChatRead(isRead, thirdMember, secondChat);
        chatReadRepository.save(secondChatThirdMember);

        isRead = true;
        ChatRead secondChatSecondMember = new ChatRead(isRead, secondMember, secondChat);
        chatReadRepository.save(secondChatSecondMember);

        // when
        List<LastChatRead> lastReadChats = chatReadRepository.findLastReadChatsBy(chatRoom.getId());

        // then
        Map<Long, Long> memberIdToLastReadChatId = lastReadChats.stream()
                .collect(Collectors.toMap(lcr -> lcr.getMemberId(), lcr -> lcr.getLastChatReadId()));

        assertThat(memberIdToLastReadChatId).hasSize(3);
        assertThat(memberIdToLastReadChatId.get(firstMember.getId())).isEqualTo(firstChat.getId());
        assertThat(memberIdToLastReadChatId.get(secondMember.getId())).isEqualTo(secondChat.getId());
        assertThat(memberIdToLastReadChatId.get(thirdMember.getId())).isEqualTo(0L);
    }

    @Test
    @DisplayName("채팅방에 특정 회원이 마지막으로 읽은 채팅 읽음 정보를 조회한다.")
    void findLastReadChatByMemberIdAndChatRoomIdTest() {
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
        List<LastChatRead> firstMemberLastRead =
                chatReadRepository.findLastReadChatBy(firstMember.getId(), chatRoom.getId());

        List<LastChatRead> secondMemberLastRead =
                chatReadRepository.findLastReadChatBy(secondMember.getId(), chatRoom.getId());

        // then
        assertThat(firstMemberLastRead).hasSize(1);
        assertThat(firstMemberLastRead.get(0).getMemberId()).isEqualTo(firstMember.getId());
        assertThat(firstMemberLastRead.get(0).getLastChatReadId()).isEqualTo(thirdChat.getId());

        assertThat(secondMemberLastRead).hasSize(1);
        assertThat(secondMemberLastRead.get(0).getMemberId()).isEqualTo(secondMember.getId());
        assertThat(secondMemberLastRead.get(0).getLastChatReadId()).isEqualTo(secondChat.getId());
    }

    @Test
    @DisplayName("Bulk Update 로 채팅방의 읽지 않은 메시지를 읽음 처리한다")
    void updateUnreadChatReadsToReadTest() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");
        Member thirdMember = fixture.savedMemberBy("thirdMember");

        ChatRoom chatRoom = fixture.savedSimpleChatRoom("title");

        Chat firstChat = fixture.savedSimpleChat("message", firstMember, chatRoom);
        chatReadRepository.save(new ChatRead(true, firstMember, firstChat));
        chatReadRepository.save(new ChatRead(true, secondMember, firstChat));
        chatReadRepository.save(new ChatRead(false, thirdMember, firstChat));

        Chat secondChat = fixture.savedSimpleChat("message", firstMember, chatRoom);
        chatReadRepository.save(new ChatRead(true, firstMember, secondChat));
        chatReadRepository.save(new ChatRead(true, secondMember, secondChat));
        chatReadRepository.save(new ChatRead(false, thirdMember, secondChat));

        Chat thirdChat = fixture.savedSimpleChat("message", secondMember, chatRoom);
        chatReadRepository.save(new ChatRead(true, firstMember, thirdChat));
        chatReadRepository.save(new ChatRead(true, secondMember, thirdChat));
        chatReadRepository.save(new ChatRead(false, thirdMember, thirdChat));

        Chat fourthChat = fixture.savedSimpleChat("message", secondMember, chatRoom);
        chatReadRepository.save(new ChatRead(false, firstMember, fourthChat));
        chatReadRepository.save(new ChatRead(true, secondMember, fourthChat));
        chatReadRepository.save(new ChatRead(false, thirdMember, fourthChat));

        // when
        chatReadRepository.updateUnreadChatReadsToRead(thirdMember.getId(), chatRoom.getId());

        // then
        Long unReadCount = chatReadRepository.findUnReadCountBy(chatRoom.getId(), thirdMember.getId());
        assertThat(unReadCount).isZero();
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
    
    private Chat createChat(String message, Member member, ChatRoom chatRoom) {
        Chat chat = new Chat(message, member, chatRoom);
        return chatRepository.save(chat);
    }
}