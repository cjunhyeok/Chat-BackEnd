package com.chat.service.dtos.chat;

import com.chat.entity.Space;
import com.chat.utils.message.MessageType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SpaceTitleChanged {

    private final MessageType messageType;
    private final Long chatRoomId;
    private final String title;

    public static SpaceTitleChanged from(Space space) {
        return SpaceTitleChanged.builder()
                .messageType(MessageType.SPACE_TITLE_CHANGED)
                .chatRoomId(space.getId())
                .title(space.getTitle())
                .build();
    }
}
