package com.chat.api.response.chatroom;

import com.chat.entity.Space;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RenameSpaceResponse {

    private Long chatRoomId;
    private String title;

    public RenameSpaceResponse(Long chatRoomId, String title) {
        this.chatRoomId = chatRoomId;
        this.title = title;
    }

    public static RenameSpaceResponse from(Space space) {
        return new RenameSpaceResponse(space.getId(), space.getTitle());
    }
}
