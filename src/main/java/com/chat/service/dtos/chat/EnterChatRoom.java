package com.chat.service.dtos.chat;

import com.chat.utils.message.BaseWebSocketMessage;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class EnterChatRoom extends BaseWebSocketMessage {
    private Long memberId;
    private Long lastReadChatId;

    public EnterChatRoom(Long memberId, Long lastReadChatId) {
        this.memberId = memberId;
        this.lastReadChatId = lastReadChatId;
    }
}
