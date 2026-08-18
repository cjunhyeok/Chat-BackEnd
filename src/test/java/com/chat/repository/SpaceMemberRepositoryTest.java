package com.chat.repository;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.SpaceMember;
import com.chat.entity.Member;
import com.chat.repository.dtos.MessageUnreadMemberCount;
import com.chat.repository.dtos.MemberUnreadCount;
import com.chat.repository.dtos.RoomUnreadMessageCount;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chat.fixture.ClientMessageIdFixture.nextClientMessageId;
import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class SpaceMemberRepositoryTest {

    @Autowired
    private SpaceMemberRepository spaceMemberRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("memberId로 참여 중인 Space 목록을 조회한다.")
    void memberId로_참여_중인_Space_목록을_조회한다() {
        // given
        String username = "username";
        Member member = createMemberBy(username);

        String firstTitle = "first";
        Space first = createSpaceBy(firstTitle);
        String secondTitle = "secondTitle";
        Space second = createSpaceBy(secondTitle);

        SpaceMember firstParticipant = spaceMemberRepository.save(SpaceMember.of(member, first));
        SpaceMember secondParticipant = spaceMemberRepository.save(SpaceMember.of(member, second));

        // when
        List<SpaceMember> findSpaceMembers = spaceMemberRepository.findAllFetchChatRoomBy(member.getId());

        // then
        assertThat(findSpaceMembers).hasSize(2);
        assertThat(findSpaceMembers).containsExactly(firstParticipant, secondParticipant);
    }

    @Test
    @DisplayName("spaceId로 참여자와 Member 정보를 fetch join으로 조회한다.")
    void spaceId로_참여자와_Member_정보를_fetch_join으로_조회한다() {
        // given
        Member member = createMemberBy("username");
        Space chatRoom = createSpaceBy("chatRoom");
        spaceMemberRepository.save(SpaceMember.of(member, chatRoom));

        em.flush();
        em.clear();

        // when
        List<SpaceMember> spaceMembers
                = spaceMemberRepository.findAllFetchMemberBy(chatRoom.getId());

        // then
        assertThat(spaceMembers).hasSize(1);
        assertThat(spaceMembers.get(0).getMember().getUsername()).isEqualTo("username");
    }

    @Test
    @DisplayName("findAllFetchMemberBy는 Member 정보를 단일 쿼리로 조회한다.")
    void findAllFetchMemberBy는_Member_정보를_단일_쿼리로_조회한다() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        Space chatRoom = createSpaceBy(title);
        spaceMemberRepository.save(SpaceMember.of(member, chatRoom));

        em.flush();
        em.clear();

        Session session = em.unwrap(Session.class);
        session.getSessionFactory().getStatistics().setStatisticsEnabled(true);
        session.getSessionFactory().getStatistics().clear();

        // when
        List<SpaceMember> spaceMembers
                = spaceMemberRepository.findAllFetchMemberBy(chatRoom.getId());
        for (SpaceMember spaceMember : spaceMembers) {
            spaceMember.getMember().getUsername();
        }
        long queryCount = session.getSessionFactory().getStatistics().getPrepareStatementCount();

        // then
        assertThat(queryCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("spaceId와 memberId로 SpaceMember를 조회한다.")
    void spaceId와_memberId로_SpaceMember를_조회한다() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        Space chatRoom = createSpaceBy(title);
        spaceMemberRepository.save(SpaceMember.of(member, chatRoom));

        // when
        SpaceMember spaceMember = spaceMemberRepository.findChatRoomBy(chatRoom.getId(), member.getId());

        // then
        assertThat(spaceMember.getSpace()).isEqualTo(chatRoom);
        assertThat(spaceMember.getMember()).isEqualTo(member);
    }

    @Test
    @DisplayName("미참여자는 SpaceMember 조회 결과가 null이다.")
    void 미참여자는_SpaceMember_조회_결과가_null이다() {
        // given
        Member memberA = createMemberBy("memberA");
        Member memberB = createMemberBy("memberB");
        Space space = createSpaceBy("room");
        spaceMemberRepository.save(SpaceMember.of(memberA, space));

        // when
        SpaceMember result = spaceMemberRepository.findChatRoomBy(space.getId(), memberB.getId());

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("여러 spaceId로 참여자와 Member 정보를 일괄 조회한다.")
    void 여러_spaceId로_참여자와_Member_정보를_일괄_조회한다() {
        // given
        Member firstMember = createMemberBy("first");
        Member secondMember = createMemberBy("second");
        Member thirdMember = createMemberBy("third");

        Space firstRoom = createSpaceBy("firstRoom");
        Space secondRoom = createSpaceBy("secondRoom");

        spaceMemberRepository.save(SpaceMember.of(firstMember, firstRoom));
        spaceMemberRepository.save(SpaceMember.of(secondMember, firstRoom));
        spaceMemberRepository.save(SpaceMember.of(thirdMember, secondRoom));

        em.flush();
        em.clear();

        // when
        List<SpaceMember> participants = spaceMemberRepository
                .findAllFetchMemberBy(List.of(firstRoom.getId(), secondRoom.getId()));

        // then
        assertThat(participants).hasSize(3);
        assertThat(participants)
                .extracting(crp -> crp.getMember().getNickname())
                .doesNotContainNull();
    }

    @Test
    @DisplayName("spaceId와 memberId로 SpaceMember를 삭제하면 조회 결과에서 제거된다.")
    void spaceId와_memberId로_SpaceMember를_삭제하면_조회_결과에서_제거된다() {
        // given
        Member member = createMemberBy("username");
        Space chatRoom = createSpaceBy("chatRoom");
        spaceMemberRepository.save(SpaceMember.of(member, chatRoom));

        // when
        spaceMemberRepository.deleteBy(chatRoom.getId(), member.getId());
        em.flush();
        em.clear();

        // then
        SpaceMember result = spaceMemberRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("SpaceMember 생성 시 lastReadMessageId 초기값은 null이다.")
    void SpaceMember_생성_시_lastReadMessageId_초기값은_null이다() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createSpaceBy("room");

        // when
        SpaceMember saved = spaceMemberRepository.save(
                SpaceMember.of(member, chatRoom)
        );

        // then
        assertThat(saved.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("lastReadMessageId가 null일 때 새로운 값으로 갱신된다.")
    void lastReadMessageId가_null일_때_새로운_값으로_갱신된다() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(
                SpaceMember.of(member, chatRoom)
        );
        em.flush();
        em.clear();

        int updated = spaceMemberRepository.updateLastReadMessageId(member.getId(), chatRoom.getId(), 100L);
        em.flush();
        em.clear();

        assertThat(updated).isEqualTo(1);
        SpaceMember found =
                spaceMemberRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(found.getLastReadMessageId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("현재 cursor보다 작은 값으로는 cursor가 갱신되지 않는다.")
    void 현재_cursor보다_작은_값으로는_cursor가_갱신되지_않는다() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(
                SpaceMember.of(member, chatRoom));
        spaceMemberRepository.updateLastReadMessageId(member.getId(), chatRoom.getId(),
                200L);
        em.flush();
        em.clear();

        // when
        int updated = spaceMemberRepository.updateLastReadMessageId(
                member.getId(), chatRoom.getId(), 100L
        );
        em.flush();
        em.clear();

        // then
        assertThat(updated).isEqualTo(0);
        SpaceMember found =
                spaceMemberRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(found.getLastReadMessageId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("현재 cursor와 동일한 값으로는 cursor가 갱신되지 않는다.")
    void 현재_cursor와_동일한_값으로는_cursor가_갱신되지_않는다() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(SpaceMember.of(member, chatRoom));
        spaceMemberRepository.updateLastReadMessageId(member.getId(), chatRoom.getId(), 100L);
        em.flush();
        em.clear();

        // when
        int updated = spaceMemberRepository.updateLastReadMessageId(
                member.getId(), chatRoom.getId(), 100L);
        em.flush();
        em.clear();

        // then
        assertThat(updated).isEqualTo(0);
        Long currentCursor = spaceMemberRepository.findLastReadMessageIdBy(
                member.getId(), chatRoom.getId());
        assertThat(currentCursor).isEqualTo(100L);
    }

    @Test
    @DisplayName("cursor 이후 메시지만 미읽음으로 집계된다.")
    void cursor_이후_메시지만_미읽음으로_집계된다() {
        // given
        Member me = createMemberBy("me");
        Member other = createMemberBy("other");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(
                SpaceMember.of(me, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(other, chatRoom));

        Message first  = messageRepository.save(Message.of("msg1", other, chatRoom, nextClientMessageId()));
        Message second = messageRepository.save(Message.of("msg2", other, chatRoom, nextClientMessageId()));
        Message third  = messageRepository.save(Message.of("msg3", other, chatRoom, nextClientMessageId()));

        spaceMemberRepository.updateLastReadMessageId(me.getId(), chatRoom.getId(),
                first.getId());
        em.flush();
        em.clear();

        // when
        List<RoomUnreadMessageCount> result = spaceMemberRepository
                .findRoomUnreadMessageCountsBy(List.of(chatRoom.getId()), me.getId());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUnreadMessageCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("cursor가 null이면 방의 전체 메시지가 미읽음으로 집계된다.")
    void cursor가_null이면_방의_전체_메시지가_미읽음으로_집계된다() {
        // given
        Member me = createMemberBy("me");
        Member sender = createMemberBy("sender");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(SpaceMember.of(me, chatRoom));
        spaceMemberRepository.save(SpaceMember.of(sender, chatRoom));

        messageRepository.save(Message.of("msg1", sender, chatRoom, nextClientMessageId()));
        messageRepository.save(Message.of("msg2", sender, chatRoom, nextClientMessageId()));
        // me의 cursor = null — updateLastReadMessageId 호출 없음

        em.flush();
        em.clear();

        // when
        List<RoomUnreadMessageCount> result = spaceMemberRepository
                .findRoomUnreadMessageCountsBy(List.of(chatRoom.getId()), me.getId());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUnreadMessageCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("cursor가 최신 메시지와 같으면 방이 미읽음 집계 결과에서 제외된다.")
    void cursor가_최신_메시지와_같으면_방이_미읽음_집계_결과에서_제외된다() {
        // given
        Member me = createMemberBy("me");
        Member sender = createMemberBy("sender");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(SpaceMember.of(me, chatRoom));
        spaceMemberRepository.save(SpaceMember.of(sender, chatRoom));

        messageRepository.save(Message.of("msg1", sender, chatRoom, nextClientMessageId()));
        Message latest = messageRepository.save(Message.of("msg2", sender, chatRoom, nextClientMessageId()));

        spaceMemberRepository.updateLastReadMessageId(me.getId(), chatRoom.getId(), latest.getId());
        em.flush();
        em.clear();

        // when
        List<RoomUnreadMessageCount> result = spaceMemberRepository
                .findRoomUnreadMessageCountsBy(List.of(chatRoom.getId()), me.getId());

        // then: cursor = latest → c.id > cursor 만족하는 메시지 없음 → INNER JOIN 0행 → 방 자체가 결과에서 제외
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("방 내 여러 멤버의 cursor 기반 미읽음 수를 일괄 조회한다.")
    void 방_내_여러_멤버의_cursor_기반_미읽음_수를_일괄_조회한다() {
        // given
        Member me = createMemberBy("me");
        Member other = createMemberBy("other");
        Member sender = createMemberBy("sender");
        Space chatRoom = createSpaceBy("room");

        spaceMemberRepository.save(
                SpaceMember.of(me, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(other, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(sender, chatRoom));

        Message first = messageRepository.save(Message.of("msg1", sender, chatRoom, nextClientMessageId()));
        messageRepository.save(Message.of("msg2", sender, chatRoom, nextClientMessageId()));

        // me: cursor null → 전체 2개 unread
        // other: cursor = first → second만 1개 unread
        spaceMemberRepository.updateLastReadMessageId(
                other.getId(), chatRoom.getId(), first.getId());
        em.flush(); em.clear();

        // when
        List<MemberUnreadCount> result = spaceMemberRepository
                .findMemberUnreadMessageCountsBy(
                        chatRoom.getId(),
                        List.of(me.getId(), other.getId()));

        // then
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(
                        MemberUnreadCount::getMemberId,
                        MemberUnreadCount::getUnreadMessageCount));

        assertThat(countMap.get(me.getId())).isEqualTo(2L);
        assertThat(countMap.get(other.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("cursor가 최신 메시지와 같으면 해당 멤버는 미읽음 집계 결과에 포함되지 않는다.")
    void cursor가_최신_메시지와_같으면_해당_멤버는_미읽음_집계_결과에_포함되지_않는다() {
        // given
        Member readerAll = createMemberBy("readerAll");
        Member sender = createMemberBy("sender");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(SpaceMember.of(readerAll, chatRoom));
        spaceMemberRepository.save(SpaceMember.of(sender, chatRoom));

        messageRepository.save(Message.of("msg1", sender, chatRoom, nextClientMessageId()));
        Message latest = messageRepository.save(Message.of("msg2", sender, chatRoom, nextClientMessageId()));

        spaceMemberRepository.updateLastReadMessageId(readerAll.getId(), chatRoom.getId(), latest.getId());
        em.flush();
        em.clear();

        // when
        List<MemberUnreadCount> result = spaceMemberRepository
                .findMemberUnreadMessageCountsBy(chatRoom.getId(), List.of(readerAll.getId()));

        // then: cursor = latest → c.id > cursor 만족하는 메시지 없음 → 해당 멤버는 결과 row 자체가 없음
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("한 번도 읽지 않은 경우 lastReadMessageId는 null이다.")
    void 한_번도_읽지_않은_경우_lastReadMessageId는_null이다() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(
                SpaceMember.of(member, chatRoom));
        em.flush();
        em.clear();

        // when
        Long result = spaceMemberRepository
                .findLastReadMessageIdBy(member.getId(), chatRoom.getId());

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("cursor 갱신 후 lastReadMessageId가 갱신된 값을 반환한다.")
    void cursor_갱신_후_lastReadMessageId가_갱신된_값을_반환한다() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(
                SpaceMember.of(member, chatRoom));
        spaceMemberRepository.updateLastReadMessageId(member.getId(), chatRoom.getId(),
                100L);
        em.flush();
        em.clear();

        // when
        Long result = spaceMemberRepository
                .findLastReadMessageIdBy(member.getId(), chatRoom.getId());

        // then
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("메시지를 보낸 발신자는 unreadMemberCount 집계에서 제외된다.")
    void 메시지를_보낸_발신자는_unreadMemberCount_집계에서_제외된다() {
        // given
        Member sender = createMemberBy("sender");
        Space chatRoom = createSpaceBy("room");
        spaceMemberRepository.save(
                SpaceMember.of(sender, chatRoom));

        Message chat = messageRepository.save(Message.of("hello", sender, chatRoom, nextClientMessageId()));
        // 발신자 cursor = chatId (saveChat 흐름 재현)
        spaceMemberRepository.updateLastReadMessageId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long count = spaceMemberRepository
                .countMessageUnreadMembers(chat.getId());

        // then: cursor = chatId → lastReadMessageId < chatId 불충족 → 제외
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("cursor가 null인 수신자는 unreadMemberCount 집계에 포함된다.")
    void cursor가_null인_수신자는_unreadMemberCount_집계에_포함된다() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver1 = createMemberBy("receiver1");
        Member receiver2 = createMemberBy("receiver2");
        Space chatRoom = createSpaceBy("room");

        spaceMemberRepository.save(
                SpaceMember.of(sender, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(receiver1, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(receiver2, chatRoom));

        Message chat = messageRepository.save(Message.of("hello", sender, chatRoom, nextClientMessageId()));
        spaceMemberRepository.updateLastReadMessageId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long count = spaceMemberRepository
                .countMessageUnreadMembers(chat.getId());

        // then: receiver1, receiver2 cursor=null → 2
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("수신자 cursor가 갱신되면 해당 메시지의 unreadMemberCount가 감소한다.")
    void 수신자_cursor가_갱신되면_해당_메시지의_unreadMemberCount가_감소한다() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver = createMemberBy("receiver");
        Space chatRoom = createSpaceBy("room");

        spaceMemberRepository.save(
                SpaceMember.of(sender, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(receiver, chatRoom));

        Message chat = messageRepository.save(Message.of("hello", sender, chatRoom, nextClientMessageId()));
        spaceMemberRepository.updateLastReadMessageId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        Long beforeRead = spaceMemberRepository
                .countMessageUnreadMembers(chat.getId());
        assertThat(beforeRead).isEqualTo(1L);

        // receiver 입장 → cursor 갱신
        spaceMemberRepository.updateLastReadMessageId(
                receiver.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long afterRead = spaceMemberRepository
                .countMessageUnreadMembers(chat.getId());

        // then
        assertThat(afterRead).isEqualTo(0L);
    }

    @Test
    @DisplayName("여러 메시지의 unreadMemberCount를 일괄 조회한다.")
    void 여러_메시지의_unreadMemberCount를_일괄_조회한다() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver = createMemberBy("receiver");
        Space chatRoom = createSpaceBy("room");

        spaceMemberRepository.save(
                SpaceMember.of(sender, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(receiver, chatRoom));

        Message first = messageRepository.save(Message.of("first", sender, chatRoom, nextClientMessageId()));
        Message second = messageRepository.save(Message.of("second", sender, chatRoom, nextClientMessageId()));

        // sender: second까지 읽음, receiver: cursor=null
        spaceMemberRepository.updateLastReadMessageId(
                sender.getId(), chatRoom.getId(), second.getId());
        em.flush(); em.clear();

        // when
        List<MessageUnreadMemberCount> result = spaceMemberRepository
                .countMessageUnreadMembers(List.of(first.getId(), second.getId()));
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(MessageUnreadMemberCount::getChatId,
                                          MessageUnreadMemberCount::getUnreadMemberCount));

        // then: 두 메시지 모두 receiver(null) → 1
        assertThat(countMap.get(first.getId())).isEqualTo(1L);
        assertThat(countMap.get(second.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("멤버마다 cursor 위치가 다를 때 메시지별 unreadMemberCount가 정확히 집계된다.")
    void 멤버마다_cursor_위치가_다를_때_메시지별_unreadMemberCount가_정확히_집계된다() {
        // given
        Member sender = createMemberBy("sender");
        Member readerOfFirst = createMemberBy("readerOfFirst");
        Member noReader = createMemberBy("noReader");
        Space chatRoom = createSpaceBy("room");

        spaceMemberRepository.save(
                SpaceMember.of(sender, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(readerOfFirst, chatRoom));
        spaceMemberRepository.save(
                SpaceMember.of(noReader, chatRoom));

        Message first = messageRepository.save(Message.of("first", sender, chatRoom, nextClientMessageId()));
        Message second = messageRepository.save(Message.of("second", sender, chatRoom, nextClientMessageId()));

        // sender: second까지, readerOfFirst: first까지, noReader: cursor=null
        spaceMemberRepository.updateLastReadMessageId(
                sender.getId(), chatRoom.getId(), second.getId());
        spaceMemberRepository.updateLastReadMessageId(
                readerOfFirst.getId(), chatRoom.getId(), first.getId());
        em.flush(); em.clear();

        // when
        List<MessageUnreadMemberCount> result = spaceMemberRepository
                .countMessageUnreadMembers(List.of(first.getId(), second.getId()));
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(MessageUnreadMemberCount::getChatId,
                                          MessageUnreadMemberCount::getUnreadMemberCount));

        // then
        // firstMessage: noReader(null) → 1
        assertThat(countMap.get(first.getId())).isEqualTo(1L);
        // secondMessage: readerOfFirst(cursor=first.id < second.id), noReader(null) → 2
        assertThat(countMap.get(second.getId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("Space 참여자는 inviteCode를 조회할 수 있다.")
    void Space_참여자는_inviteCode를_조회할_수_있다() {
        // given
        Member member = createMemberBy("member");
        Space space = createSpaceBy("개발팀");
        spaceMemberRepository.save(SpaceMember.of(member, space));

        em.flush();
        em.clear();

        // when
        java.util.Optional<String> result = spaceMemberRepository
                .findInviteCodeBySpaceIdAndMemberId(space.getId(), member.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(space.getInviteCode());
        assertThat(result.get()).hasSize(32);
    }

    @Test
    @DisplayName("Space 미참여자는 inviteCode 조회 시 Optional.empty를 반환한다.")
    void Space_미참여자는_inviteCode_조회_시_Optional_empty를_반환한다() {
        // given
        Member member = createMemberBy("member");
        Member stranger = createMemberBy("stranger");
        Space space = createSpaceBy("개발팀");
        spaceMemberRepository.save(SpaceMember.of(member, space));

        em.flush();
        em.clear();

        // when
        java.util.Optional<String> result = spaceMemberRepository
                .findInviteCodeBySpaceIdAndMemberId(space.getId(), stranger.getId());

        // then
        assertThat(result).isEmpty();
    }

    private Member createMemberBy(String username) {
        String commonPassword = "commonPassword";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }

    private Space createSpaceBy(String title) {
        Space chatRoom = Space.of(title);
        return spaceRepository.save(chatRoom);
    }
}
