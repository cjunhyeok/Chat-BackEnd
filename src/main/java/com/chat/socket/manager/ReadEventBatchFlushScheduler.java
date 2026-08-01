package com.chat.socket.manager;

import com.chat.service.dtos.chat.ReadEventBatch;
import com.chat.service.dtos.chat.ReadEventBatchItem;
import com.chat.socket.listener.MessageBroadcastListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadEventBatchFlushScheduler {

    private final ReadEventBatchAccumulator readEventBatchAccumulator;
    private final MessageBroadcastListener messageBroadcastListener;

    @Scheduled(fixedRate = 100)
    public void flush() {
        Map<ReadEventBatchKey, ReadEventBatchRange> drained = readEventBatchAccumulator.drain();

        if (drained.isEmpty()) {
            return;
        }

        Map<Long, List<ReadEventBatchItem>> itemsByChatRoomId = new HashMap<>();

        for (Map.Entry<ReadEventBatchKey, ReadEventBatchRange> entry : drained.entrySet()) {
            ReadEventBatchKey key = entry.getKey();
            ReadEventBatchRange range = entry.getValue();

            ReadEventBatchItem item = new ReadEventBatchItem(
                    key.getMemberId(),
                    range.getPreviousLastReadChatId(),
                    range.getCurrentLastReadChatId()
            );

            itemsByChatRoomId
                    .computeIfAbsent(key.getChatRoomId(), chatRoomId -> new ArrayList<>())
                    .add(item);
        }

        for (Map.Entry<Long, List<ReadEventBatchItem>> entry : itemsByChatRoomId.entrySet()) {
            Long chatRoomId = entry.getKey();
            ReadEventBatch batch = new ReadEventBatch(chatRoomId, entry.getValue());

            try {
                messageBroadcastListener.broadcastReadEventBatch(batch);
            } catch (Exception e) {
                log.error("ReadEventBatch 전송 요청 실패: chatRoomId={}", chatRoomId, e);
            }
        }
    }
}
