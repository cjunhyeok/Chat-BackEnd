package com.chat.service.dtos.chat;

import com.chat.utils.message.BaseWebSocketMessage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@NoArgsConstructor
public class UpdateChatRoom extends BaseWebSocketMessage {

    private Long chatRoomId;
    private String title;
    private String lastMessage;
    private Long unReadCount;
    private LocalDateTime createdDate;

    public UpdateChatRoom(Long chatRoomId, String title, String lastMessage, Long unReadCount, LocalDateTime createdDate) {
        this.chatRoomId = chatRoomId;
        this.title = title;
        this.lastMessage = lastMessage;
        this.unReadCount = unReadCount;
        this.createdDate = createdDate;
    }
}
