package com.chat.utils.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    CHAT_MESSAGE("CHAT_MESSAGE"),
    CHAT_ENTER("채팅방 접속"),
    UPDATE_CHAT_ROOM("UPDATE_CHAT_ROOM"),
    ;

    private final String description;
}
