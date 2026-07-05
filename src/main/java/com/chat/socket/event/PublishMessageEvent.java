package com.chat.socket.event;

import com.chat.service.dtos.chat.BroadcastChat;
import com.chat.service.dtos.chat.UpdateChatRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class PublishMessageEvent {

    private final BroadcastChat broadcastChat;
    private final Long chatRoomId;
    private final Map<Long, UpdateChatRoom> updatesByMemberId;
    private final long publishedAtNanos;
}
