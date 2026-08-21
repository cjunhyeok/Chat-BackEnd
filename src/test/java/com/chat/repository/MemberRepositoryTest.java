package com.chat.repository;

import com.chat.entity.Space;
import com.chat.entity.SpaceMember;
import com.chat.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
    private SpaceRepository spaceRepository;
    @Autowired
    private SpaceMemberRepository spaceMemberRepository;
    
    @Test
    @DisplayName("등록된 username은 existsByUsername이 true를 반환한다.")
    void 등록된_username은_existsByUsername이_true를_반환한다() {
        // given
        String existUsername = "existUsername";
        createMemberBy(existUsername);

        // when
        boolean isExist = memberRepository.existsByUsername(existUsername);

        // then
        assertThat(isExist).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 username은 existsByUsername이 false를 반환한다.")
    void 존재하지_않는_username은_existsByUsername이_false를_반환한다() {
        // when
        boolean result = memberRepository.existsByUsername("ghost");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("등록된 username으로 조회하면 해당 Member를 반환한다.")
    void 등록된_username으로_조회하면_해당_Member를_반환한다() {
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

    @Test
    @DisplayName("존재하지 않는 username으로 조회하면 Optional.empty를 반환한다.")
    void 존재하지_않는_username으로_조회하면_Optional_empty를_반환한다() {
        // when
        Optional<Member> result = memberRepository.findByUsername("ghost");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("spaceId로 참여한 전체 memberId 목록을 조회한다.")
    void spaceId로_참여한_전체_memberId_목록을_조회한다() {
        // given
        Member firstMember = createMemberBy("first");
        Member secondMember = createMemberBy("second");
        Member thirdMember = createMemberBy("third");

        Space savedChatRoom = spaceRepository.save(Space.of("title"));

        spaceMemberRepository.save(SpaceMember.of(firstMember, savedChatRoom));
        spaceMemberRepository.save(SpaceMember.of(secondMember, savedChatRoom));
        spaceMemberRepository.save(SpaceMember.of(thirdMember, savedChatRoom));

        // when
        List<Long> memberIds = memberRepository.findMemberIdsIn(savedChatRoom.getId());

        // then
        assertThat(memberIds)
                .hasSize(3)
                .containsExactlyInAnyOrder(firstMember.getId(), secondMember.getId(), thirdMember.getId());
    }

    @Test
    @DisplayName("findMemberIdsIn은 다른 Space의 memberId를 포함하지 않는다.")
    void findMemberIdsIn은_다른_Space의_memberId를_포함하지_않는다() {
        // given
        Space spaceA = spaceRepository.save(Space.of("spaceA"));
        Space spaceB = spaceRepository.save(Space.of("spaceB"));

        Member memberA = createMemberBy("memberA");
        Member memberB = createMemberBy("memberB");
        Member memberC = createMemberBy("memberC");

        spaceMemberRepository.save(SpaceMember.of(memberA, spaceA));
        spaceMemberRepository.save(SpaceMember.of(memberB, spaceA));
        spaceMemberRepository.save(SpaceMember.of(memberC, spaceB));

        // when
        List<Long> memberIds = memberRepository.findMemberIdsIn(spaceA.getId());

        // then
        assertThat(memberIds)
                .containsExactlyInAnyOrder(memberA.getId(), memberB.getId());
    }

    @Test
    @DisplayName("동일한 username을 가진 Member는 중복 저장할 수 없다.")
    void 동일한_username을_가진_Member는_중복_저장할_수_없다() {
        // given
        String duplicatedUsername = "duplicatedUsername";
        Member firstMember = Member.of(duplicatedUsername, "firstPassword", "firstNickname");
        Member duplicateMember = Member.of(duplicatedUsername, "secondPassword", "secondNickname");

        memberRepository.saveAndFlush(firstMember);

        // when & then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicateMember))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Member createMemberBy(String username) {
        String commonPassword = "password";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }
}