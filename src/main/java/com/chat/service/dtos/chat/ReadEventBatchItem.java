package com.chat.service.dtos.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReadEventBatchItem {

    private final Long memberId;
    private final Long previousLastReadChatId;
    private final Long currentLastReadChatId;
}
