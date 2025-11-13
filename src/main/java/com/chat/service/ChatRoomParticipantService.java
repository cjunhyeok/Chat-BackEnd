package com.chat.service;

import com.chat.entity.ChatRoomParticipant;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.ChatRoomParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomParticipantService {

    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Transactional
    public void enterChatRoom(Long chatRoomId, Long memberId) {

        validateIds(chatRoomId, memberId);

        ChatRoomParticipant chatRoomParticipant = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        chatRoomParticipant.enterChatRoom();
    }

    @Transactional
    public void leaveChatRoom(Long chatRoomId, Long memberId) {

        validateIds(chatRoomId, memberId);

        ChatRoomParticipant chatRoomParticipant = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        chatRoomParticipant.leaveChatRoom();
    }

    private void validateIds(Long chatRoomId, Long memberId) {
        if (chatRoomId == null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST);
        }

        if (memberId == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }
}
