package com.chat.api;

import com.chat.api.response.member.GetMembersResponse;
import com.chat.api.request.member.JoinRequest;
import com.chat.api.response.member.JoinResponse;
import com.chat.api.request.member.LoginRequest;
import com.chat.service.MemberService;
import com.chat.utils.consts.SessionConst;
import com.chat.service.dtos.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    @PostMapping("/api/member")
    public Result<JoinResponse> join(@RequestBody JoinRequest request) {

        Long savedMemberId = memberService.join(request);

        return Result.<JoinResponse>builder()
                .data(new JoinResponse(savedMemberId))
                .status(HttpStatus.OK)
                .message("회원가입이 완료됐습니다.")
                .build();
    }

    @PostMapping("/api/member/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request,
                        HttpServletRequest servletRequest) {

        LoginResponse loginResponse = memberService.login(request);

        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(SessionConst.SESSION_ID, loginResponse.getMemberId());

        return Result.<LoginResponse>builder()
                .status(HttpStatus.OK)
                .data(loginResponse)
                .message("로그인이 완료됐습니다.")
                .build();
    }

    @GetMapping("/api/members")
    public Result<List<GetMembersResponse>> getMembers() {

        List<GetMembersResponse> response = memberService.findMembers();

        return Result.<List<GetMembersResponse>>builder()
                .status(HttpStatus.OK)
                .data(response)
                .message("사용자 정보 조회에 성공했습니다.")
                .build();
    }
}
