package com.chat.api;

import com.chat.api.response.member.GetMembersResponse;
import com.chat.api.request.member.ChangeNicknameRequest;
import com.chat.api.request.member.ChangePasswordRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

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

        // 세션 고정(Session Fixation) 방지 : 기존 세션 무효화 후 새로 생성
        HttpSession old = servletRequest.getSession(false);
        if (old != null) old.invalidate();

        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(SessionConst.SESSION_ID, loginResponse.getMemberId());

        return Result.<LoginResponse>builder()
                .status(HttpStatus.OK)
                .data(loginResponse)
                .message("로그인이 완료됐습니다.")
                .build();
    }

    @GetMapping("/api/members")
    public Result<List<GetMembersResponse>> getMembers(
            @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        List<GetMembersResponse> response = memberService.findMembers();

        return Result.<List<GetMembersResponse>>builder()
                .status(HttpStatus.OK)
                .data(response)
                .message("사용자 정보 조회에 성공했습니다.")
                .build();
    }

    @PatchMapping("/api/member/nickname")
    public Result<Void> changeNickname(
            @RequestBody ChangeNicknameRequest request,
            @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        memberService.changeNickname(loginMemberId, request.getNickname());

        return Result.<Void>builder()
                .status(HttpStatus.OK)
                .message("닉네임이 변경됐습니다.")
                .build();
    }

    @PatchMapping("/api/member/password")
    public Result<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        memberService.changePassword(loginMemberId, request.getCurrentPassword(), request.getNewPassword());

        return Result.<Void>builder()
                .status(HttpStatus.OK)
                .message("비밀번호가 변경됐습니다.")
                .build();
    }
}
