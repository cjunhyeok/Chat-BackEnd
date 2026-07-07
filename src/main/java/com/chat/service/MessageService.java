package com.chat.service;

import com.chat.entity.*;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.repository.dtos.DiscussionSummary;
import com.chat.repository.dtos.MessageUnreadMemberCount;
import com.chat.service.dtos.MessageHistory;
import com.chat.service.dtos.MessageHistoryResponse;
import com.chat.service.dtos.SaveMessageData;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.socket.event.PublishReadEvent;
import com.chat.utils.consts.MessageMetricNames;
import com.chat.utils.valid.IdValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private static final int PAGE_SIZE = 30;

    private final ApplicationEventPublisher publisher;

    private final BroadcastDataBuilder broadcastDataBuilder;

    private final MeterRegistry meterRegistry;

    private final MessageRepository messageRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final MemberRepository memberRepository;
    private final DiscussionRepository discussionRepository;

    public SaveMessageData findMessageData(Long chatId) {
        Timer.Sample dataLoadSample = Timer.start(meterRegistry);
        try {
            Message findChat = messageRepository.findById(chatId).orElseThrow(
                    () -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND)
            );
            Long unreadMemberCount = spaceMemberRepository.countMessageUnreadMembers(chatId);

            return SaveMessageData
                    .builder()
                    .chatId(findChat.getId())
                    .createdDate(findChat.getCreatedDate())
                    .unreadMemberCount(unreadMemberCount)
                    .build();
        } finally {
            dataLoadSample.stop(meterRegistry.timer(MessageMetricNames.MESSAGE_DATA_LOAD_DURATION));
        }
    }

    @Transactional
    public Long saveMessage(Long senderId, Long chatRoomId, String message) {
        return saveMessage(senderId, chatRoomId, message, null);
    }

    @Transactional
    public Long saveMessage(Long senderId, Long chatRoomId, String message, String clientMessageId) {

        if (clientMessageId != null) {
            Timer.Sample idempotencyCheckSample = Timer.start(meterRegistry);
            Optional<Message> existing;
            try {
                existing = messageRepository.findByClientMessageId(clientMessageId);
            } finally {
                idempotencyCheckSample.stop(meterRegistry.timer(MessageMetricNames.MESSAGE_IDEMPOTENCY_CHECK_DURATION));
            }
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }

        Member findSender = memberRepository.findById(senderId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );
        Space findChatRoom = spaceRepository.findById(chatRoomId).orElseThrow(
                () -> new CustomException(ErrorCode.SPACE_NOT_FOUND)
        );

        Message savedChat = messageRepository.save(Message.of(message, findSender, findChatRoom, clientMessageId));
        updateSenderCursorOnSend(findSender.getId(), findChatRoom.getId(), savedChat);

        return savedChat.getId();
    }

    private void updateSenderCursorOnSend(Long senderId, Long chatRoomId, Message chat) {
        Timer.Sample cursorUpdateSample = Timer.start(meterRegistry);
        try {
            spaceMemberRepository.updateLastReadMessageId(senderId, chatRoomId, chat.getId());
        } finally {
            cursorUpdateSample.stop(meterRegistry.timer(MessageMetricNames.MESSAGE_CURSOR_UPDATE_DURATION));
        }
    }

    @Transactional
    public MessageHistoryResponse findMessageHistory(Long chatRoomId, Long memberId, Long beforeChatId) {

        Member findMember = memberRepository.findById(memberId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );

        return createMessageHistoryResponse(chatRoomId, findMember.getId(), beforeChatId);
    }

    private MessageHistoryResponse createMessageHistoryResponse(Long chatRoomId, Long memberId, Long beforeChatId) {

        PageRequest pageable = PageRequest.of(0, PAGE_SIZE + 1);
        List<Message> chats;

        if (beforeChatId == null) {
            chats = messageRepository.findLatestMessages(chatRoomId, pageable);
        } else {
            chats = messageRepository.findMessagesBeforeId(chatRoomId, beforeChatId, pageable);
        }

        if (chats.isEmpty()) {
            return new MessageHistoryResponse(null, List.of(), false);
        }

        boolean hasMore = chats.size() > PAGE_SIZE;
        if (hasMore) {
            chats = new ArrayList<>(chats.subList(0, PAGE_SIZE));
        } else {
            chats = new ArrayList<>(chats);
        }
        Collections.reverse(chats);

        List<Long> chatIds = chats.stream()
                .map(Message::getId)
                .toList();

        Long lastReadMessageId = null;

        if (beforeChatId == null) {
            lastReadMessageId = spaceMemberRepository
                    .findLastReadMessageIdBy(memberId, chatRoomId);

            Long currentLastReadChatId = chats.get(chats.size() - 1).getId();
            int updatedCount = spaceMemberRepository.updateLastReadMessageId(
                    memberId, chatRoomId, currentLastReadChatId
            );

            if (updatedCount > 0) {
                Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId, Set.of(memberId));
                publisher.publishEvent(new PublishReadEvent(
                        memberId,
                        chatRoomId,
                        lastReadMessageId,
                        currentLastReadChatId,
                        updatesByMemberId
                ));
            }
        }

        Map<Long, Long> unreadMemberCountMap = spaceMemberRepository
                .countMessageUnreadMembers(chatIds).stream()
                .collect(Collectors.toMap(
                        MessageUnreadMemberCount::getChatId,
                        MessageUnreadMemberCount::getUnreadMemberCount
                ));

        Map<Long, DiscussionSummary> discussionSummaryMap = discussionRepository
                .findDiscussionSummariesByRootMessageIds(chatIds).stream()
                .collect(Collectors.toMap(
                        DiscussionSummary::getMessageId,
                        ds -> ds
                ));

        List<MessageHistory> messages = new ArrayList<>(chats.size());
        for (Message chat : chats) {
            Member sender = chat.getMember();
            Long unreadCount = unreadMemberCountMap.getOrDefault(chat.getId(), 0L);
            DiscussionSummary ds = discussionSummaryMap.get(chat.getId());

            messages.add(MessageHistory.builder()
                    .chatId(chat.getId())
                    .senderNickname(sender.getNickname())
                    .senderId(sender.getId())
                    .message(chat.getContent())
                    .unreadMemberCount(unreadCount)
                    .createdDate(chat.getCreatedDate())
                    .discussionId(ds != null ? ds.getDiscussionId() : null)
                    .discussionMessageCount(ds != null ? ds.getDiscussionMessageCount() : 0L)
                    .build());
        }

        return new MessageHistoryResponse(lastReadMessageId, messages, hasMore);
    }

    @Transactional
    public void onRoomActive(Long memberId, Long chatRoomId) {
        Optional<Long> latestChatIdOpt = messageRepository.findLastMessageIdBy(chatRoomId);
        if (latestChatIdOpt.isEmpty()) {
            return;
        }
        Long latestChatId = latestChatIdOpt.get();

        Long previousLastReadChatId =
                spaceMemberRepository.findLastReadMessageIdBy(memberId, chatRoomId);

        int updateCount =
                spaceMemberRepository.updateLastReadMessageId(memberId, chatRoomId, latestChatId);

        if (updateCount == 0) {
            return;
        }

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId, Set.of(memberId));
        publisher.publishEvent(new PublishReadEvent(
                memberId,
                chatRoomId,
                previousLastReadChatId,
                latestChatId,
                updatesByMemberId
        ));
    }

    @Transactional
    public void onReadUpTo(Long memberId, Long chatRoomId, Long lastReadMessageId) {
        IdValidator.requireMessageId(lastReadMessageId);

        if (!messageRepository.existsByIdAndSpaceId(lastReadMessageId, chatRoomId)) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        Long previousLastReadChatId =
                spaceMemberRepository.findLastReadMessageIdBy(memberId, chatRoomId);

        int updateCount =
                spaceMemberRepository.updateLastReadMessageId(memberId, chatRoomId, lastReadMessageId);

        if (updateCount == 0) {
            return;
        }

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId, Set.of(memberId));
        publisher.publishEvent(new PublishReadEvent(
                memberId,
                chatRoomId,
                previousLastReadChatId,
                lastReadMessageId,
                updatesByMemberId
        ));
    }
}
