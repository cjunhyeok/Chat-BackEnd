package com.chat.service.dtos.chat;

import com.chat.utils.message.BaseWebSocketMessage;
import com.chat.service.dtos.SaveChatData;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@NoArgsConstructor
public class SendChat extends BaseWebSocketMessage {

    private Long senderId;
    private String senderNickname;
    private Long chatRoomId;
    private String message;
    private Long chatId;
    private Long unReadCount;
    private LocalDateTime createDate;

    private SendChat(Long senderId, String senderNickname, Long chatRoomId, String message) {
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.chatRoomId = chatRoomId;
        this.message = message;
    }

    public void updateSavedChat(SaveChatData saveChatData) {
       this.chatId = saveChatData.getChatId();
       this.unReadCount = saveChatData.getUnReadCount();
       this.createDate = saveChatData.getCreateDate();
    }
}
