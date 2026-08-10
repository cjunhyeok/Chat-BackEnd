package com.chat.service;

import com.chat.api.request.member.JoinRequest;
import com.chat.api.request.member.LoginRequest;
import com.chat.api.response.member.GetMembersResponse;
import com.chat.entity.Member;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.MemberRepository;
import com.chat.service.dtos.LoginResponse;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Transactional
@SpringBootTest
class MemberServiceTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private WebsocketSessionManager websocketSessionManager;
    @Autowired
    private SpaceManager spaceManager;

    @BeforeEach
    void setUp() {
        websocketSessionManager.clearAll();
        spaceManager.clearAll();
    }

    @Test
    @DisplayName("회원가입 시 비밀번호가 BCrypt로 인코딩되어 저장된다.")
    void 회원가입_시_비밀번호가_BCrypt로_인코딩되어_저장된다() {
        // given
        JoinRequest request = JoinRequest.builder()
                .username("username")
                .password("password")
                .nickname("nickname")
                .build();

        // when
        Long joinMemberId = memberService.join(request);

        // then
        Member findMember = memberRepository.findById(joinMemberId).get();
        assertThat(findMember.getId()).isEqualTo(joinMemberId);
        assertThat(findMember.getUsername()).isEqualTo("username");
        assertThat(findMember.getPassword()).isNotEqualTo("password");
    }

    @Test
    @DisplayName("올바른 자격증명으로 로그인하면 memberId와 닉네임을 반환한다.")
    void 올바른_자격증명으로_로그인하면_memberId와_닉네임을_반환한다() {
        // given
        Long joinMemberId = joinMember("username", "password", "nickname");

        // login()은 Propagation.NOT_SUPPORTED로 실행되어 별도 TX에서 DB를 조회한다.
        // join()으로 저장한 데이터가 별도 TX에서 보이려면 먼저 커밋되어야 한다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        LoginRequest request = LoginRequest.builder()
                .username("username")
                .password("password")
                .build();

        // when
        LoginResponse response = memberService.login(request);

        // then
        assertThat(response.getMemberId()).isEqualTo(joinMemberId);
        assertThat(response.getNickname()).isEqualTo("nickname");

        // T1에서 커밋된 데이터는 자동 롤백되지 않으므로 직접 정리
        memberRepository.deleteById(joinMemberId);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @Test
    @DisplayName("가입된 전체 회원 목록을 조회한다.")
    void 가입된_전체_회원_목록을_조회한다() {
        // given
        String firstUsername = "first";
        Long firstMemberId = joinSimpleMember(firstUsername);
        String secondUsername = "second";
        Long secondMemberId = joinSimpleMember(secondUsername);
        String thirdUsername = "third";
        Long thirdMemberId = joinSimpleMember(thirdUsername);

        // when
        List<GetMembersResponse> members = memberService.findMembers();

        // then
        assertThat(members).hasSize(3);
    }

    @Test
    @DisplayName("유효한 닉네임으로 변경하면 닉네임이 갱신된다.")
    void 유효한_닉네임으로_변경하면_닉네임이_갱신된다() {
        // given
        Long memberId = joinSimpleMember("user");
        String newNickname = "newNickname";

        // when
        memberService.changeNickname(memberId, newNickname);

        // then
        Member findMember = memberRepository.findById(memberId).get();
        assertThat(findMember.getNickname()).isEqualTo(newNickname);
    }

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 변경된다.")
    void 현재_비밀번호가_맞으면_새_비밀번호로_변경된다() {
        // given
        Long memberId = joinMember("user", "oldPassword", "nickname");
        String oldPassword = memberRepository.findById(memberId).get().getPassword();

        // when
        memberService.changePassword(memberId, "oldPassword", "newPassword");

        // then
        Member findMember = memberRepository.findById(memberId).get();
        assertThat(findMember.getPassword()).isNotEqualTo(oldPassword);
    }

    @Test
    @DisplayName("이미 존재하는 username으로 회원가입하면 DUPLICATED_USERNAME 예외가 발생한다.")
    void 이미_존재하는_username으로_회원가입하면_DUPLICATED_USERNAME_예외가_발생한다() {
        // given
        String duplicatedUsername = "username";
        joinSimpleMember(duplicatedUsername);

        JoinRequest duplicateRequest = JoinRequest.builder()
                .username(duplicatedUsername)
                .password("anotherPassword")
                .nickname("anotherNickname")
                .build();

        // when & then
        assertThatThrownBy(() -> memberService.join(duplicateRequest))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATED_USERNAME);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 비밀번호 변경 시 예외가 발생한다.")
    void 현재_비밀번호가_틀리면_비밀번호_변경_시_예외가_발생한다() {
        // given
        Long memberId = joinMember("user", "correctPassword", "nickname");

        // when & then
        assertThatThrownBy(() -> memberService.changePassword(memberId, "wrongPassword", "newPassword"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("removeSession을 호출하면 Space 구독, 활성 상태, 세션 registry가 모두 정리된다.")
    void removeSession을_호출하면_Space_구독_활성_상태_세션_registry가_모두_정리된다() {
        // given
        Long memberId = 1L;
        Long chatRoomId = 100L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        websocketSessionManager.addSession(memberId, session);
        spaceManager.registerSession(session);
        spaceManager.addSessionToSpace(session, chatRoomId);

        assertThat(websocketSessionManager.getSessionBy(memberId)).isNotEmpty();
        assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).isNotEmpty();
        assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isTrue();

        // when
        memberService.removeSession(memberId, session);

        // then
        assertThat(websocketSessionManager.getSessionBy(memberId)).isEmpty();
        assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).isEmpty();
        assertThat(spaceManager.isSpaceActive(memberId, chatRoomId)).isFalse();
    }

    private Long joinMember(String username, String password, String nickname) {
        JoinRequest request = JoinRequest.builder()
                .username(username)
                .password(password)
                .nickname(nickname)
                .build();

        return memberService.join(request);
    }

    private Long joinSimpleMember(String username) {
        JoinRequest request = JoinRequest.builder()
                .username(username)
                .password("password")
                .nickname("nickname")
                .build();

        return memberService.join(request);
    }
}