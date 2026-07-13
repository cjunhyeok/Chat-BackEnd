package com.chat.socket.event;

import com.chat.service.dtos.chat.SpaceInvited;
import lombok.Getter;

import java.util.Set;

@Getter
public class PublishSpaceInvitedEvent {

    private final SpaceInvited spaceInvited;
    private final Long chatRoomId;
    private final Set<Long> recipientMemberIds;

    public PublishSpaceInvitedEvent(SpaceInvited spaceInvited, Long chatRoomId, Set<Long> recipientMemberIds) {
        this.spaceInvited = spaceInvited;
        this.chatRoomId = chatRoomId;
        this.recipientMemberIds = Set.copyOf(recipientMemberIds);
    }
}
