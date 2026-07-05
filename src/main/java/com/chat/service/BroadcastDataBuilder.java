package com.chat.service;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.repository.dtos.MemberUnreadCount;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.utils.consts.MessageMetricNames;
import com.chat.utils.message.MessageType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BroadcastDataBuilder {

    private final MemberRepository memberRepository;
    private final SpaceRepository spaceRepository;
    private final MessageRepository messageRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final MeterRegistry meterRegistry;

    public Map<Long, UpdateChatRoom> build(Long chatRoomId) {
        Timer.Sample updateBuildSample = Timer.start(meterRegistry);
        try {
            if (chatRoomId == null) return Map.of();

            List<Long> memberIds = memberRepository.findMemberIdsIn(chatRoomId);
            if (memberIds.isEmpty()) return Map.of();

            return build(chatRoomId, new HashSet<>(memberIds));
        } finally {
            updateBuildSample.stop(meterRegistry.timer(MessageMetricNames.MESSAGE_UPDATE_BUILD_DURATION));
        }
    }

    public Map<Long, UpdateChatRoom> build(Long chatRoomId, Set<Long> targetMemberIds) {
        if (chatRoomId == null || targetMemberIds == null || targetMemberIds.isEmpty()) {
            return Map.of();
        }

        Space space = spaceRepository.findById(chatRoomId).orElseThrow(
                () -> new CustomException(ErrorCode.SPACE_NOT_FOUND)
        );

        Message lastChat = messageRepository
                .findLastMessageBy(chatRoomId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);

        Map<Long, Long> unreadCountMap = spaceMemberRepository
                .findMemberUnreadMessageCountsBy(chatRoomId, new ArrayList<>(targetMemberIds))
                .stream()
                .collect(Collectors.toMap(
                        MemberUnreadCount::getMemberId,
                        MemberUnreadCount::getUnreadMessageCount
                ));

        return targetMemberIds.stream()
                .collect(Collectors.toMap(
                        memberId -> memberId,
                        memberId -> UpdateChatRoom.builder()
                                .messageType(MessageType.UPDATE_CHAT_ROOM)
                                .chatRoomId(chatRoomId)
                                .title(space.getTitle())
                                .lastMessage(lastChat != null ? lastChat.getContent() : null)
                                .lastChatId(lastChat != null ? lastChat.getId() : null)
                                .createdDate(lastChat != null ? lastChat.getCreatedDate() : null)
                                .unreadMessageCount(unreadCountMap.getOrDefault(memberId, 0L))
                                .build()
                ));
    }
}
