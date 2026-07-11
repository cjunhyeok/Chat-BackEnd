package com.chat.utils.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    CHAT_MESSAGE("채팅 메시지"),
    CHAT_ENTER("채팅방 접속"),
    ENTER_ROOM("채팅방 입장 요청"),
    ENTER_ROOM_ACK("채팅방 입장 성공 응답"),
    UPDATE_CHAT_ROOM("채팅방 목록 갱신"),
    ROOM_MESSAGE_SUMMARY_UPDATED("채팅방 마지막 메시지 요약 갱신"),
    READ_EVENT("읽음 이벤트"),
    READ_UP_TO("읽음 처리 요청"),
    ROOM_ACTIVE("방 활성화"),
    ROOM_INACTIVE("방 비활성화"),
    DISCUSSION_MESSAGE("Discussion 메시지 전송"),
    DISCUSSION_MESSAGE_EVENT("Discussion 메시지 이벤트"),
    ERROR("에러 응답"),
    ;

    private final String description;
}
