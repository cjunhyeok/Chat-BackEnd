package com.chat.repository;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.chat.fixture.ClientMessageIdFixture.nextClientMessageId;
import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("spaceId로 마지막 메시지를 내림차순으로 조회한다.")
    void spaceId로_마지막_메시지를_내림차순으로_조회한다() {
        // given
        String firstUser = "first";
        Member firstMember = createMember(firstUser);
        String secondUser = "second";
        Member secondMember = createMember(secondUser);

        String title = "title";
        Space chatRoom = createSpaceBy(title);

        String firstMessage = "first";
        Message firstChat = Message.of(firstMessage, firstMember, chatRoom, nextClientMessageId());
        messageRepository.save(firstChat);

        String secondMessage = "second";
        Message secondChat = Message.of(secondMessage, secondMember, chatRoom, nextClientMessageId());
        messageRepository.save(secondChat);

        Pageable limitOne = createLimitOne();

        // when
        List<Message> lastChatArray = messageRepository.findLastMessageBy(chatRoom.getId(), limitOne);

        // then
        assertThat(lastChatArray).hasSize(1);
        assertThat(lastChatArray.get(0)).isEqualTo(secondChat);
    }

    @Test
    @DisplayName("여러 spaceId로 각 방의 마지막 메시지를 일괄 조회한다.")
    void 여러_spaceId로_각_방의_마지막_메시지를_일괄_조회한다() {
        // given
        Member member = createMember("user");

        Space firstRoom = createSpaceBy("firstRoom");
        Space secondRoom = createSpaceBy("secondRoom");

        messageRepository.save(Message.of("first-1", member, firstRoom, nextClientMessageId()));
        Message lastOfFirst = messageRepository.save(Message.of("first-2", member, firstRoom, nextClientMessageId()));

        Message lastOfSecond = messageRepository.save(Message.of("second-1", member, secondRoom, nextClientMessageId()));

        // when
        List<Message> lastChats = messageRepository
                .findLastMessagesBy(List.of(firstRoom.getId(), secondRoom.getId()));

        // then
        assertThat(lastChats).hasSize(2);
        assertThat(lastChats)
                .extracting(Message::getId)
                .containsExactlyInAnyOrder(lastOfFirst.getId(), lastOfSecond.getId());
    }

    @Test
    @DisplayName("메시지가 없는 방은 일괄 마지막 메시지 조회 결과에 포함되지 않는다.")
    void 메시지가_없는_방은_일괄_마지막_메시지_조회_결과에_포함되지_않는다() {
        // given
        Member member = createMember("user");

        Space roomWithChat = createSpaceBy("roomWithChat");
        Space emptyRoom = createSpaceBy("emptyRoom");

        Message chat = messageRepository.save(Message.of("message", member, roomWithChat, nextClientMessageId()));

        // when
        List<Message> lastChats = messageRepository
                .findLastMessagesBy(List.of(roomWithChat.getId(), emptyRoom.getId()));

        // then
        assertThat(lastChats).hasSize(1);
        assertThat(lastChats.get(0).getId()).isEqualTo(chat.getId());
    }

    @Test
    @DisplayName("최신 메시지를 id 내림차순으로 Pageable 크기만큼 조회한다.")
    void 최신_메시지를_id_내림차순으로_Pageable_크기만큼_조회한다() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        Message first = messageRepository.save(Message.of("first", member, chatRoom, nextClientMessageId()));
        Message second = messageRepository.save(Message.of("second", member, chatRoom, nextClientMessageId()));
        Message third = messageRepository.save(Message.of("third", member, chatRoom, nextClientMessageId()));

        Pageable limit2 = PageRequest.of(0, 2);

        // when
        List<Message> result = messageRepository.findLatestMessages(chatRoom.getId(), limit2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(third);
        assertThat(result.get(1)).isEqualTo(second);
    }

    @Test
    @DisplayName("최신 메시지 조회 시 다른 방의 메시지는 포함되지 않는다.")
    void 최신_메시지_조회_시_다른_방의_메시지는_포함되지_않는다() {
        // given
        Member member = createMember("user");
        Space targetRoom = createSpaceBy("target");
        Space otherRoom = createSpaceBy("other");

        Message targetChat = messageRepository.save(Message.of("target message", member, targetRoom, nextClientMessageId()));
        messageRepository.save(Message.of("other message", member, otherRoom, nextClientMessageId()));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findLatestMessages(targetRoom.getId(), limit10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(targetChat);
    }

    @Test
    @DisplayName("메시지가 없는 방의 최신 메시지 조회는 빈 리스트를 반환한다.")
    void 메시지가_없는_방의_최신_메시지_조회는_빈_리스트를_반환한다() {
        // given
        Space emptyRoom = createSpaceBy("empty");
        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findLatestMessages(emptyRoom.getId(), limit10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("cursor 이전 메시지를 id 내림차순으로 조회한다.")
    void cursor_이전_메시지를_id_내림차순으로_조회한다() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        Message first = messageRepository.save(Message.of("first", member, chatRoom, nextClientMessageId()));
        Message second = messageRepository.save(Message.of("second", member, chatRoom, nextClientMessageId()));
        Message third = messageRepository.save(Message.of("third", member, chatRoom, nextClientMessageId()));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findMessagesBeforeId(chatRoom.getId(), third.getId(), limit10);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(second);
        assertThat(result.get(1)).isEqualTo(first);
    }

    @Test
    @DisplayName("cursor 이전에 메시지가 없으면 빈 리스트를 반환한다.")
    void cursor_이전에_메시지가_없으면_빈_리스트를_반환한다() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        Message firstChat = messageRepository.save(Message.of("only message", member, chatRoom, nextClientMessageId()));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findMessagesBeforeId(chatRoom.getId(), firstChat.getId(), limit10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("cursor 기반 조회 시 다른 방의 메시지는 포함되지 않는다.")
    void cursor_기반_조회_시_다른_방의_메시지는_포함되지_않는다() {
        // given
        Member member = createMember("user");
        Space targetRoom = createSpaceBy("target");
        Space otherRoom = createSpaceBy("other");

        messageRepository.save(Message.of("other message", member, otherRoom, nextClientMessageId()));
        Message targetFirst = messageRepository.save(Message.of("target first", member, targetRoom, nextClientMessageId()));
        Message targetSecond = messageRepository.save(Message.of("target second", member, targetRoom, nextClientMessageId()));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findMessagesBeforeId(
                targetRoom.getId(), targetSecond.getId(), limit10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(targetFirst);
    }

    @Test
    @DisplayName("메시지가 있는 방의 최신 messageId를 반환한다.")
    void 메시지가_있는_방의_최신_messageId를_반환한다() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        messageRepository.save(Message.of("first", member, chatRoom, nextClientMessageId()));
        Message latest = messageRepository.save(Message.of("second", member, chatRoom, nextClientMessageId()));

        // when
        Optional<Long> result = messageRepository.findLastMessageIdBy(chatRoom.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(latest.getId());
    }

    @Test
    @DisplayName("메시지가 없는 방은 최신 messageId 조회 시 Optional.empty를 반환한다.")
    void 메시지가_없는_방은_최신_messageId_조회_시_Optional_empty를_반환한다() {
        // given
        Space emptyRoom = createSpaceBy("empty");

        // when
        Optional<Long> result = messageRepository.findLastMessageIdBy(emptyRoom.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("최신 messageId 조회 시 다른 방의 메시지는 포함되지 않는다.")
    void 최신_messageId_조회_시_다른_방의_메시지는_포함되지_않는다() {
        // given
        Member member = createMember("user");
        Space targetRoom = createSpaceBy("target");
        Space otherRoom = createSpaceBy("other");

        Message targetChat = messageRepository.save(Message.of("target", member, targetRoom, nextClientMessageId()));
        messageRepository.save(Message.of("other", member, otherRoom, nextClientMessageId()));
        messageRepository.save(Message.of("other2", member, otherRoom, nextClientMessageId()));

        // when
        Optional<Long> result = messageRepository.findLastMessageIdBy(targetRoom.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(targetChat.getId());
    }

    @Test
    @DisplayName("clientMessageId로 메시지를 조회한다.")
    void clientMessageId로_메시지를_조회한다() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");
        String clientMessageId = nextClientMessageId();

        Message saved = messageRepository.save(Message.of("message", member, chatRoom, clientMessageId));

        // when
        Optional<Message> result = messageRepository.findByClientMessageId(clientMessageId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("존재하지 않는 clientMessageId로 조회하면 빈 Optional을 반환한다.")
    void 존재하지_않는_clientMessageId로_조회하면_빈_Optional을_반환한다() {
        // when
        Optional<Message> result = messageRepository.findByClientMessageId("non-existent-client-message-id");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 room 소속 messageId는 existsByIdAndSpaceId가 true를 반환한다.")
    void 같은_room_소속_messageId는_existsByIdAndSpaceId가_true를_반환한다() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");
        Message chat = messageRepository.save(Message.of("message", member, chatRoom, nextClientMessageId()));

        // when
        boolean exists = messageRepository.existsByIdAndSpaceId(chat.getId(), chatRoom.getId());

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("다른 room 소속 messageId는 existsByIdAndSpaceId가 false를 반환한다.")
    void 다른_room_소속_messageId는_existsByIdAndSpaceId가_false를_반환한다() {
        // given
        Member member = createMember("user");
        Space targetRoom = createSpaceBy("target");
        Space otherRoom = createSpaceBy("other");
        Message otherChat = messageRepository.save(Message.of("other message", member, otherRoom, nextClientMessageId()));

        // when
        boolean exists = messageRepository.existsByIdAndSpaceId(otherChat.getId(), targetRoom.getId());

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 messageId는 existsByIdAndSpaceId가 false를 반환한다.")
    void 존재하지_않는_messageId는_existsByIdAndSpaceId가_false를_반환한다() {
        // given
        Space chatRoom = createSpaceBy("room");

        // when
        boolean exists = messageRepository.existsByIdAndSpaceId(999_999_999L, chatRoom.getId());

        // then
        assertThat(exists).isFalse();
    }

    private Member createMember(String username) {
        String commonPassword = "password";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }

    private Space createSpaceBy(String title) {
        Space chatRoom = Space.of(title);
        return spaceRepository.save(chatRoom);
    }

    private Pageable createLimitOne() {
        return PageRequest.of(0, 1);
    }
}
