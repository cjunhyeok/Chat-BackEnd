package com.chat.service;

import com.chat.api.response.member.GetMembersResponse;
import com.chat.api.request.member.JoinRequest;
import com.chat.api.request.member.LoginRequest;
import com.chat.entity.Member;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.MemberRepository;
import com.chat.service.dtos.LoginResponse;
import com.chat.service.utils.PasswordEncoder;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.LoginMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final WebsocketSessionManager websocketSessionManager;
    private final SpaceManager spaceManager;
    private final MeterRegistry meterRegistry;

    @Transactional
    public Long join(JoinRequest request) {
        boolean isMemberExist = memberRepository.existsByUsername(request.getUsername());

        if (isMemberExist) {
            throw new CustomException(ErrorCode.DUPLICATED_USERNAME);
        }

        validateRawPassword(request.getPassword());

        Member member = Member.of(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname());

        return memberRepository.save(member).getId();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LoginResponse login(LoginRequest request) {
        Timer.Sample dbQuerySample = Timer.start(meterRegistry);
        Member findMember;
        try {
            findMember = memberRepository.findByUsername(request.getUsername()).orElseThrow(
                    () -> new CustomException(ErrorCode.USERNAME_NOT_MATCH)
            );
        } finally {
            dbQuerySample.stop(meterRegistry.timer(LoginMetricNames.LOGIN_DB_QUERY_DURATION));
        }

        boolean isMatch = passwordEncoder.match(request.getPassword(), findMember.getPassword());
        if (!isMatch) {
            throw new CustomException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        return LoginResponse
                .builder()
                .memberId(findMember.getId())
                .nickname(findMember.getNickname())
                .build();
    }

    public List<GetMembersResponse> findMembers() {
        List<Member> findMembers = memberRepository.findAll();

        List<GetMembersResponse> getMembersResponses = new ArrayList<>();
        for (Member findMember : findMembers) {
            GetMembersResponse response = GetMembersResponse
                    .builder()
                    .memberId(findMember.getId())
                    .username(findMember.getUsername())
                    .nickname(findMember.getNickname())
                    .build();

            getMembersResponses.add(response);
        }

        return getMembersResponses;
    }

    @Transactional
    public void changeNickname(Long memberId, String nickname) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        member.changeNickname(nickname);
    }

    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        boolean isMatch = passwordEncoder.match(currentPassword, member.getPassword());
        if (!isMatch) {
            throw new CustomException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        validateRawPassword(newPassword);

        member.changePassword(passwordEncoder.encode(newPassword));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void removeSession(Long memberId, WebSocketSession closingSession) {
        spaceManager.removeSessionFromSpace(closingSession);
        websocketSessionManager.removeSession(memberId, closingSession);
        spaceManager.removeSessionState(closingSession);
    }

    private void validateRawPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new CustomException(ErrorCode.EMPTY_PASSWORD);
        }
    }
}
