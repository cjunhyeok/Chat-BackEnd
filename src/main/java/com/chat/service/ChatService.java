package com.chat.service;

import com.chat.entity.*;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.service.dtos.ChatHistory;
import com.chat.service.dtos.SaveChatData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatReadRepository chatReadRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final MemberRepository memberRepository;

    public SaveChatData findChatData(Long chatId) {
        Chat findChat = chatRepository.findById(chatId).orElseThrow(
                () -> new CustomException(ErrorCode.CHAT_NOT_EXIST)
        );
        Long unReadcount = chatReadRepository.findUnReadCountBy(chatId);

        return SaveChatData
                .builder()
                .chatId(findChat.getId())
                .createDate(findChat.getCreatedDate())
                .unReadCount(unReadcount)
                .build();
    }

    @Transactional
    public Long saveChat(Long senderId, Long chatRoomId, String message) {

        Member findSender = memberRepository.findById(senderId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );
        ChatRoom findChatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow(
                () -> new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST)
        );

        Chat savedChat = chatRepository.save(new Chat(message, findSender, findChatRoom));
        saveChatRead(findSender.getId(), findChatRoom.getId(), savedChat);

        return savedChat.getId();
    }

    private void saveChatRead(Long senderId, Long chatRoomId, Chat chat) {

        // 내가 보낸 메시지 이전의 메시지 모두 읽음처리
        chatReadRepository.updateUnreadChatReadsToRead(senderId, chatRoomId);

        // 읽음 저장
        List<ChatRoomParticipant> findChatRoomParticipants
                = chatRoomParticipantRepository
                .findAllFetchMemberBy(chatRoomId);

        for (ChatRoomParticipant findChatRoomParticipant : findChatRoomParticipants) {

            Member participant = findChatRoomParticipant.getMember();

            if (!participant.getId().equals(senderId)) {
                boolean isRead = findChatRoomParticipant.isParticipate();
                ChatRead chatRead = new ChatRead(isRead, participant, chat);
                chatReadRepository.save(chatRead);
            } else {
                chatReadRepository.save(new ChatRead(true, participant, chat));
            }
        }
    }

    @Transactional
    public List<ChatHistory> findChatHistory(Long chatRoomId, Long memberId) {

        Member findMember = memberRepository.findById(memberId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );

        return createChatHistoryResponse(chatRoomId, findMember.getId());
    }

    private List<ChatHistory> createChatHistoryResponse(Long chatRoomId, Long memberId) {
        List<Chat> findChatHistory = chatRepository.findChatHistory(chatRoomId);

        List<ChatHistory> chatHistories = new ArrayList<>();

        for (Chat chat : findChatHistory) {

            Member sender = chat.getMember();

            ChatRead findChatRead = chatReadRepository.findBy(chat.getId(), memberId);
            if (findChatRead != null) {
                findChatRead.updateIsReadTrue();
            }

            Long unReadCount = chatReadRepository.findUnReadCountBy(chat.getId());

            ChatHistory chatHistory = ChatHistory.builder()
                    .chatId(chat.getId())
                    .senderNickname(sender.getNickname())
                    .senderId(sender.getId())
                    .message(chat.getMessage())
                    .unReadCount(unReadCount)
                    .createdDate(chat.getCreatedDate())
                    .build();

            chatHistories.add(chatHistory);
        }

        return chatHistories;
    }
}
