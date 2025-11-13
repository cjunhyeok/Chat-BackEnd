package com.chat.fixture;

import com.chat.api.request.member.LoginRequest;
import com.chat.entity.Member;
import com.chat.repository.MemberRepository;
import com.chat.service.MemberService;
import com.chat.service.dtos.LoginResponse;
import com.chat.service.utils.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class MemberFixture {

    private static final String PASSWORD = "password";
    private static final String NICKNAME = "nickname";

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public Member saveEncryptPasswordBy(String username) {
        Member member = Member.of(
                username,
                passwordEncoder.encode(PASSWORD),
                NICKNAME
        );
        return memberRepository.save(member);
    }

    public LoginResponse loginBy(String username, String requestUrl) {
        LoginRequest request = LoginRequest
                .builder()
                .username(username)
                .password(PASSWORD)
                .build();

        return memberService.login(request);
    }

    public String loginRequestBy(String username, int port) {
        LoginRequest request = LoginRequest
                .builder()
                .username(username)
                .password(PASSWORD)
                .build();

        TestRestTemplate restTemplate = new TestRestTemplate();
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/member/login",
                request,
                String.class
        );
        return loginResponse.getHeaders()
                .getFirst(HttpHeaders.SET_COOKIE)
                .split(";")[0]
                .replace("JSESSIONID=", "");
    }

}
