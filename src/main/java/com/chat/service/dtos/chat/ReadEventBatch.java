package com.chat.service.dtos.chat;

import com.chat.utils.message.MessageType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class ReadEventBatch {

    private final MessageType messageType = MessageType.READ_EVENT_BATCH;
    private final Long chatRoomId;
    private final List<ReadEventBatchItem> reads;
}
