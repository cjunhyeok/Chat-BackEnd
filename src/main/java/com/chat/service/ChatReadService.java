package com.chat.service;

import com.chat.repository.ChatReadRepository;
import com.chat.repository.MemberRepository;
import com.chat.service.dtos.LastChatRead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatReadService {

    private final ChatReadRepository chatReadRepository;
    private final MemberRepository memberRepository;

    public List<LastChatRead> findMembersChatReadIn(Long chatRoomId) {

        Map<Long, Long> membersLastChatId = getMembersLastChatIdsMapBy(chatRoomId);

        List<Long> memberIdsInChatRoom = memberRepository.findMemberIdsIn(chatRoomId);
        List<LastChatRead> finalLastReads = memberIdsInChatRoom.stream()
                .map(memberId -> {
                    Long lastReadId = membersLastChatId.getOrDefault(memberId, 0L);
                    return new LastChatRead(memberId, lastReadId);
                })
                .collect(Collectors.toList());

        return finalLastReads;
    }

    private Map<Long, Long> getMembersLastChatIdsMapBy(Long chatRoomId) {
        // 각 회원별 마지막 읽은 메시지 ID 조회
        List<LastChatRead> lastReadChats = chatReadRepository.findLastReadChatsBy(chatRoomId);
        // 1. lastReadChats를 Map<Long, Long> 형태로 변환
        //    key: memberId, value: lastChatReadId
        return lastReadChats.stream()
                .collect(Collectors.toMap(LastChatRead::getMemberId, LastChatRead::getLastChatReadId));
    }

    public LastChatRead findLastChatBy(Long memberId, Long chatRoomId) {
        return chatReadRepository.findLastReadChatBy(memberId, chatRoomId)
                .stream()
                .findFirst()
                .orElse(null);
    }
}
