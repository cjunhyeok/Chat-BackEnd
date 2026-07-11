package com.chat.service.dtos.chat;

import com.chat.entity.Message;
import com.chat.utils.message.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RoomMessageSummaryUpdated {

    private final MessageType messageType;
    private final Long chatRoomId;
    private final Long lastChatId;
    private final String lastMessage;
    private final LocalDateTime createdDate;

    public static RoomMessageSummaryUpdated from(Message message) {
        return RoomMessageSummaryUpdated.builder()
                .messageType(MessageType.ROOM_MESSAGE_SUMMARY_UPDATED)
                .chatRoomId(message.getSpace().getId())
                .lastChatId(message.getId())
                .lastMessage(message.getContent())
                .createdDate(message.getCreatedDate())
                .build();
    }
}
