package com.chat.service;

import com.chat.api.response.chatroom.SpaceInviteCodeResponse;
import com.chat.api.response.chatroom.SpaceInviteInfoResponse;
import com.chat.api.response.chatroom.SpaceMemberResponse;
import com.chat.api.response.chatroom.SpaceSummaryResponse;
import com.chat.entity.*;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.repository.dtos.RoomUnreadMessageCount;
import com.chat.service.dtos.SaveMessageData;
import com.chat.service.dtos.SaveSpaceDTO;
import com.chat.service.dtos.chat.BroadcastChat;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.socket.event.PublishMessageEvent;
import com.chat.socket.event.PublishUpdateEvent;
import com.chat.socket.manager.SpaceManager;
import com.chat.utils.consts.MessageMetricNames;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.MessageType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceService {

    private final ApplicationEventPublisher publisher;
    private final BroadcastDataBuilder broadcastDataBuilder;
    private final SpaceManager spaceManager;
    private final MeterRegistry meterRegistry;

    private final MessageService messageService;

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final MessageRepository messageRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void broadCastMessage(Long memberId, SendChat sendChat) {
        Timer.Sample flowSample = Timer.start(meterRegistry);
        try {
            Long chatRoomId = sendChat.getChatRoomId();
            Long savedMessageId = messageService.saveMessage(memberId, chatRoomId, sendChat.getMessage(), sendChat.getClientMessageId());

            Member sender = memberRepository.findById(memberId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );

            SaveMessageData messageData = messageService.findMessageData(savedMessageId);

            BroadcastChat broadcastChat = BroadcastChat.builder()
                    .messageType(MessageType.CHAT_MESSAGE)
                    .senderId(memberId)
                    .senderNickname(sender.getNickname())
                    .chatRoomId(chatRoomId)
                    .message(sendChat.getMessage())
                    .chatId(messageData.getChatId())
                    .unreadMemberCount(messageData.getUnreadMemberCount())
                    .createdDate(messageData.getCreatedDate())
                    .clientMessageId(sendChat.getClientMessageId())
                    .build();

            Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId);
            publisher.publishEvent(new PublishMessageEvent(broadcastChat, chatRoomId, updatesByMemberId, System.nanoTime()));
        } finally {
            flowSample.stop(meterRegistry.timer(MessageMetricNames.MESSAGE_FLOW_DURATION));
        }
    }

    @Transactional
    public Long saveSpace(SaveSpaceDTO saveSpaceDTO) {
        Member sender = memberRepository.findById(saveSpaceDTO.getSenderId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        Space space = Space.of(saveSpaceDTO.getTitle());
        Space savedSpace = spaceRepository.save(space);

        spaceMemberRepository.save(SpaceMember.of(sender, savedSpace));

        return savedSpace.getId();
    }

    public List<SpaceSummaryResponse> findSpaces(Long memberId) {

        Member findMember = memberRepository.findById(memberId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );

        return createSpaceSummaryResponse(findMember.getId());
    }

    public void validateParticipant(Long memberId, Long chatRoomId) {
        SpaceMember participant = spaceMemberRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.SPACE_NOT_FOUND);
        }
    }

    private List<SpaceSummaryResponse> createSpaceSummaryResponse(Long memberId) {

        // 참여 채팅방 목록 조회
        List<SpaceMember> participants
                = spaceMemberRepository.findAllFetchChatRoomBy(memberId);

        if (participants.isEmpty()) {
            return List.of();
        }

        List<Long> chatRoomIds = participants
                .stream()
                .map(crp -> crp.getSpace().getId())
                .toList();

        // 채팅방별 마지막 메시지 일괄 조회
        Map<Long, Message> lastChatMap = messageRepository
                .findLastMessagesBy(chatRoomIds)
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getSpace().getId(),
                        c -> c
                ));

        // 채팅방별 안 읽은 메시지 수 일괄 조회
        Map<Long, Long> unreadMessageCountMap = spaceMemberRepository
                .findRoomUnreadMessageCountsBy(chatRoomIds, memberId)
                .stream()
                .collect(Collectors.toMap(
                        RoomUnreadMessageCount::getChatRoomId,
                        RoomUnreadMessageCount::getUnreadMessageCount
                ));

        return participants
                .stream()
                .map(crp -> {
                    Long chatRoomId = crp.getSpace().getId();
                    Message lastChat = lastChatMap.get(chatRoomId);

                    return SpaceSummaryResponse.builder()
                            .chatRoomId(chatRoomId)
                            .title(crp.getSpace().getTitle())
                            .lastMessage(lastChat != null ? lastChat.getContent() : null)
                            .lastChatId(lastChat != null ? lastChat.getId() : null)
                            .createdDate(lastChat != null ? lastChat.getCreatedDate() : null)
                            .unreadMessageCount(unreadMessageCountMap.getOrDefault(chatRoomId, 0L))
                            .build();
                })
                .toList();
    }

    @Transactional
    public void leaveSpace(Long memberId, Long chatRoomId) {
        SpaceMember participant
                = spaceMemberRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.SPACE_NOT_FOUND);
        }

        spaceMemberRepository.deleteBy(chatRoomId, memberId);

        Set<WebSocketSession> sessions = new HashSet<>(spaceManager.getWebSocketSessionBy(chatRoomId));
        for (WebSocketSession session : sessions) {
            Long sessionMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
            if (memberId.equals(sessionMemberId)) {
                spaceManager.removeSpaceSession(chatRoomId, session);
            }
        }

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId);
        publisher.publishEvent(new PublishUpdateEvent(chatRoomId, updatesByMemberId));
    }

    @Transactional
    public void renameSpace(Long memberId, Long chatRoomId, String title) {

        SpaceMember participant = spaceMemberRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.SPACE_NOT_FOUND);
        }

        Space space = spaceRepository.findById(chatRoomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));
        space.rename(title);

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId);
        publisher.publishEvent(new PublishUpdateEvent(chatRoomId, updatesByMemberId));
    }

    public List<SpaceMemberResponse> findSpaceMembers(Long memberId, Long chatRoomId) {

        SpaceMember participant = spaceMemberRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.SPACE_NOT_FOUND);
        }

        return spaceMemberRepository.findAllFetchMemberBy(chatRoomId)
                .stream()
                .map(crp -> new SpaceMemberResponse(
                        crp.getMember().getId(),
                        crp.getMember().getNickname()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SpaceInviteCodeResponse getSpaceInviteCode(Long loginMemberId, Long spaceId) {
        String inviteCode = spaceMemberRepository
                .findInviteCodeBySpaceIdAndMemberId(spaceId, loginMemberId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        return new SpaceInviteCodeResponse(inviteCode);
    }

    @Transactional(readOnly = true)
    public SpaceInviteInfoResponse findSpaceByInviteCode(Long loginMemberId, String inviteCode) {
        Space space = spaceRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

        long memberCount = spaceMemberRepository.countBySpaceId(space.getId());
        boolean alreadyJoined = spaceMemberRepository.findChatRoomBy(space.getId(), loginMemberId) != null;

        return new SpaceInviteInfoResponse(space.getId(), space.getTitle(), memberCount, alreadyJoined);
    }

    @Transactional
    public Long joinSpaceByInviteCode(Long loginMemberId, String inviteCode) {
        Space space = spaceRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

        SpaceMember existing = spaceMemberRepository.findChatRoomBy(space.getId(), loginMemberId);
        if (existing != null) {
            return space.getId();
        }

        Member member = memberRepository.findById(loginMemberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        spaceMemberRepository.save(SpaceMember.of(member, space));

        return space.getId();
    }

    @Transactional
    public void inviteMembers(Long memberId, Long chatRoomId, Set<Long> inviteeIds) {

        SpaceMember participant = spaceMemberRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.SPACE_NOT_FOUND);
        }

        Set<Long> existingMemberIds = spaceMemberRepository
                .findAllFetchMemberBy(chatRoomId)
                .stream()
                .map(crp -> crp.getMember().getId())
                .collect(Collectors.toSet());

        Space space = spaceRepository.findById(chatRoomId).orElseThrow(
                () -> new CustomException(ErrorCode.SPACE_NOT_FOUND)
        );

        List<Member> invitees = memberRepository.findAllById(inviteeIds);
        for (Member invitee : invitees) {
            if (!existingMemberIds.contains(invitee.getId())) {
                spaceMemberRepository.save(SpaceMember.of(invitee, space));
            }
        }

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId);
        publisher.publishEvent(new PublishUpdateEvent(chatRoomId, updatesByMemberId));
    }
}
