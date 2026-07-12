package com.chat.socket.event;

import com.chat.service.dtos.chat.SpaceTitleChanged;
import lombok.Getter;

import java.util.Set;

@Getter
public class PublishSpaceTitleChangedEvent {

    private final SpaceTitleChanged spaceTitleChanged;
    private final Set<Long> recipientMemberIds;

    public PublishSpaceTitleChangedEvent(SpaceTitleChanged spaceTitleChanged, Set<Long> recipientMemberIds) {
        this.spaceTitleChanged = spaceTitleChanged;
        this.recipientMemberIds = Set.copyOf(recipientMemberIds);
    }
}
