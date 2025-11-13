package com.chat.service;

import com.chat.api.request.member.JoinRequest;
import com.chat.api.request.member.LoginRequest;
import com.chat.api.response.member.GetMembersResponse;
import com.chat.entity.Member;
import com.chat.repository.MemberRepository;
import com.chat.service.dtos.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    @DisplayName("사용자가 회원가입한다.")
    void joinTest() {
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
    @DisplayName("사용자가 로그인한다.")
    void loginTest() {
        // given
        String username = "username";
        String password = "password";
        String nickname = "nickname";
        Long joinMemberId = joinMember(username, password, nickname);

        LoginRequest request = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        // when
        LoginResponse response = memberService.login(request);

        // then
        assertThat(response.getMemberId()).isEqualTo(joinMemberId);
        assertThat(response.getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("가입된 모든 사용자를 조회한다.")
    void findMembersTest() {
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

    // todo removeSession 테스트 필요

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