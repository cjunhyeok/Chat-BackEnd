package com.chat.socket.manager;

import java.util.Objects;

public class ReadEventBatchRange {

    private final Long previousLastReadChatId;
    private final Long currentLastReadChatId;

    public ReadEventBatchRange(Long previousLastReadChatId, Long currentLastReadChatId) {
        this.previousLastReadChatId = previousLastReadChatId;
        this.currentLastReadChatId = currentLastReadChatId;
    }

    public Long getPreviousLastReadChatId() {
        return previousLastReadChatId;
    }

    public Long getCurrentLastReadChatId() {
        return currentLastReadChatId;
    }

    public ReadEventBatchRange merge(ReadEventBatchRange other) {
        Long mergedPrevious = (this.previousLastReadChatId == null || other.previousLastReadChatId == null)
                ? null
                : Math.min(this.previousLastReadChatId, other.previousLastReadChatId);
        Long mergedCurrent = Math.max(this.currentLastReadChatId, other.currentLastReadChatId);

        return new ReadEventBatchRange(mergedPrevious, mergedCurrent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadEventBatchRange)) {
            return false;
        }
        ReadEventBatchRange that = (ReadEventBatchRange) o;
        return Objects.equals(previousLastReadChatId, that.previousLastReadChatId)
                && Objects.equals(currentLastReadChatId, that.currentLastReadChatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(previousLastReadChatId, currentLastReadChatId);
    }
}
