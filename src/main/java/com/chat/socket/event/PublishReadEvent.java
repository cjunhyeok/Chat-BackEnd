package com.chat.socket.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PublishReadEvent {
    private final Long memberId;
    private final Long chatRoomId;
    private final Long previousLastReadChatId;
    private final Long currentLastReadChatId;
}
