package com.chat.socket.event;

import com.chat.service.dtos.chat.BroadcastChat;
import com.chat.service.dtos.chat.RoomMessageSummaryUpdated;
import lombok.Getter;

import java.util.Set;

@Getter
public class PublishMessageEvent {

    private final BroadcastChat broadcastChat;
    private final Long chatRoomId;
    private final RoomMessageSummaryUpdated roomMessageSummaryUpdated;
    private final Set<Long> recipientMemberIds;
    private final long publishedAtNanos;

    public PublishMessageEvent(BroadcastChat broadcastChat,
                                Long chatRoomId,
                                RoomMessageSummaryUpdated roomMessageSummaryUpdated,
                                Set<Long> recipientMemberIds,
                                long publishedAtNanos) {
        this.broadcastChat = broadcastChat;
        this.chatRoomId = chatRoomId;
        this.roomMessageSummaryUpdated = roomMessageSummaryUpdated;
        this.recipientMemberIds = Set.copyOf(recipientMemberIds);
        this.publishedAtNanos = publishedAtNanos;
    }
}
