package com.chat.utils.valid;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;

public class IdValidator {

    public static void requireIds(Long memberId, Long ChatRoomId) {
        if (memberId == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }

        requireChatRoomId(ChatRoomId);
    }

    public static void requireChatRoomId(Long ChatRoomId) {
        if (ChatRoomId == null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST);
        }
    }
}
