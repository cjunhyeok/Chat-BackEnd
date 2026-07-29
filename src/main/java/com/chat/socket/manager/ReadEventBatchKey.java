package com.chat.socket.manager;

import java.util.Objects;

public class ReadEventBatchKey {

    private final Long chatRoomId;
    private final Long memberId;

    public ReadEventBatchKey(Long chatRoomId, Long memberId) {
        this.chatRoomId = chatRoomId;
        this.memberId = memberId;
    }

    public Long getChatRoomId() {
        return chatRoomId;
    }

    public Long getMemberId() {
        return memberId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadEventBatchKey)) {
            return false;
        }
        ReadEventBatchKey that = (ReadEventBatchKey) o;
        return Objects.equals(chatRoomId, that.chatRoomId) && Objects.equals(memberId, that.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatRoomId, memberId);
    }
}
