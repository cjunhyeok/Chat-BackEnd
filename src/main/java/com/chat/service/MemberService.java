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
import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final WebsocketSessionManager websocketSessionManager;
    private final ChatRoomManager chatRoomManager;
    private final ChatRoomParticipantService chatRoomParticipantService;

    @Transactional
    public Long join(JoinRequest request) {
        boolean isMemberExist = memberRepository.existsByUsername(request.getUsername());

        if (isMemberExist) {
            throw new CustomException(ErrorCode.DUPLICATED_USERNAME);
        }

        Member member = Member.of(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname());

        return memberRepository.save(member).getId();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Member findMember = memberRepository.findByUsername(request.getUsername()).orElseThrow(
                () -> new CustomException(ErrorCode.USERNAME_NOT_MATCH)
        );

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

    public void removeSession(Long memberId) {

        Set<Long> chatRoomIds = chatRoomManager.getChatRoomIdsBy(memberId);
        if (chatRoomIds == null || chatRoomIds.isEmpty()) {
            return;
        }

        for (Long chatRoomId : chatRoomIds) {
            chatRoomParticipantService.leaveChatRoom(chatRoomId, memberId);
            chatRoomManager.removeChatRoomSession(chatRoomId, memberId);
        }

        websocketSessionManager.removeSession(memberId);
    }
}
