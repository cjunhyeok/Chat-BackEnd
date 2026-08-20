package com.chat.service;

import com.chat.api.request.member.JoinRequest;
import com.chat.api.request.member.LoginRequest;
import com.chat.api.response.member.GetMembersResponse;
import com.chat.entity.Member;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.MemberRepository;
import com.chat.service.dtos.LoginResponse;
import com.chat.service.utils.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class MemberServiceTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Nested
    @DisplayName("join")
    class Join {

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
            assertThat(passwordEncoder.match("password", findMember.getPassword())).isTrue();
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

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        @DisplayName("null 또는 blank 비밀번호로 회원가입하면 EMPTY_PASSWORD 예외가 발생한다.")
        void null_또는_blank_비밀번호로_회원가입하면_EMPTY_PASSWORD_예외가_발생한다(String invalidPassword) {
            // given
            String username = "invalidPasswordJoinUser";
            JoinRequest request = JoinRequest.builder()
                    .username(username)
                    .password(invalidPassword)
                    .nickname("nickname")
                    .build();

            // when & then
            assertThatThrownBy(() -> memberService.join(request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.EMPTY_PASSWORD);

            assertThat(memberRepository.findByUsername(username)).isEmpty();
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("올바른 자격증명으로 로그인하면 memberId와 닉네임을 반환한다.")
        void 올바른_자격증명으로_로그인하면_memberId와_닉네임을_반환한다() {
            // given
            Long joinMemberId = joinMember("username", "password", "nickname");

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
        @DisplayName("존재하지 않는 username으로 로그인하면 USERNAME_NOT_MATCH 예외가 발생한다.")
        void 존재하지_않는_username으로_로그인하면_USERNAME_NOT_MATCH_예외가_발생한다() {
            // given
            LoginRequest request = LoginRequest.builder()
                    .username("ghostUsername")
                    .password("password")
                    .build();

            // when & then
            assertThatThrownBy(() -> memberService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USERNAME_NOT_MATCH);
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인하면 PASSWORD_NOT_MATCH 예외가 발생한다.")
        void 잘못된_비밀번호로_로그인하면_PASSWORD_NOT_MATCH_예외가_발생한다() {
            // given
            Long joinMemberId = joinMember("loginPasswordTestUser", "correctPassword", "nickname");

            TestTransaction.flagForCommit();
            TestTransaction.end();
            TestTransaction.start();

            LoginRequest request = LoginRequest.builder()
                    .username("loginPasswordTestUser")
                    .password("wrongPassword")
                    .build();

            // when & then
            assertThatThrownBy(() -> memberService.login(request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_NOT_MATCH);

            // T1에서 커밋된 데이터는 자동 롤백되지 않으므로 직접 정리
            memberRepository.deleteById(joinMemberId);
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    @Nested
    @DisplayName("findMembers")
    class FindMembers {

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
            assertThat(members)
                    .extracting(
                            GetMembersResponse::getMemberId,
                            GetMembersResponse::getUsername,
                            GetMembersResponse::getNickname
                    )
                    .containsExactlyInAnyOrder(
                            tuple(firstMemberId, firstUsername, "nickname"),
                            tuple(secondMemberId, secondUsername, "nickname"),
                            tuple(thirdMemberId, thirdUsername, "nickname")
                    );
        }
    }

    @Nested
    @DisplayName("changeNickname")
    class ChangeNickname {

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
        @DisplayName("존재하지 않는 회원의 닉네임을 변경하면 MEMBER_NOT_FOUND 예외가 발생한다.")
        void 존재하지_않는_회원의_닉네임을_변경하면_MEMBER_NOT_FOUND_예외가_발생한다() {
            // given
            Long nonExistentMemberId = 999999L;

            // when & then
            assertThatThrownBy(() -> memberService.changeNickname(nonExistentMemberId, "newNickname"))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 변경된다.")
        void 현재_비밀번호가_맞으면_새_비밀번호로_변경된다() {
            // given
            String currentPassword = "oldPassword";
            String newPassword = "newPassword";
            Long memberId = joinMember("user", currentPassword, "nickname");

            // when
            memberService.changePassword(memberId, currentPassword, newPassword);

            // then
            Member findMember = memberRepository.findById(memberId).get();
            assertThat(passwordEncoder.match(newPassword, findMember.getPassword())).isTrue();
            assertThat(passwordEncoder.match(currentPassword, findMember.getPassword())).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 회원의 비밀번호를 변경하면 MEMBER_NOT_FOUND 예외가 발생한다.")
        void 존재하지_않는_회원의_비밀번호를_변경하면_MEMBER_NOT_FOUND_예외가_발생한다() {
            // given
            Long nonExistentMemberId = 999999L;

            // when & then
            assertThatThrownBy(() -> memberService.changePassword(nonExistentMemberId, "currentPassword", "newPassword"))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 PASSWORD_NOT_MATCH 예외가 발생한다.")
        void 현재_비밀번호가_틀리면_PASSWORD_NOT_MATCH_예외가_발생한다() {
            // given
            Long memberId = joinMember("user", "correctPassword", "nickname");

            // when & then
            assertThatThrownBy(() -> memberService.changePassword(memberId, "wrongPassword", "newPassword"))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_NOT_MATCH);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        @DisplayName("null 또는 blank 새 비밀번호로 변경하면 EMPTY_PASSWORD 예외가 발생한다.")
        void null_또는_blank_새_비밀번호로_변경하면_EMPTY_PASSWORD_예외가_발생한다(String invalidNewPassword) {
            // given
            Long memberId = joinMember("changePasswordTestUser", "currentPassword", "nickname");
            Member member = memberRepository.findById(memberId).get();
            String originalEncodedPassword = member.getPassword();

            // when & then
            assertThatThrownBy(() -> memberService.changePassword(memberId, "currentPassword", invalidNewPassword))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.EMPTY_PASSWORD);

            assertThat(member.getPassword()).isEqualTo(originalEncodedPassword);
        }
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
