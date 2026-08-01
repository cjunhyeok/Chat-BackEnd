package com.chat.socket.manager;

import com.chat.socket.event.PublishReadEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReadEventBatchAccumulator {

    private final Map<ReadEventBatchKey, ReadEventBatchRange> pending = new ConcurrentHashMap<>();

    public void enqueue(PublishReadEvent event) {
        ReadEventBatchKey key = new ReadEventBatchKey(event.getChatRoomId(), event.getMemberId());
        ReadEventBatchRange incoming = new ReadEventBatchRange(
                event.getPreviousLastReadChatId(),
                event.getCurrentLastReadChatId()
        );

        pending.merge(key, incoming, ReadEventBatchRange::merge);
    }

    public Map<ReadEventBatchKey, ReadEventBatchRange> drain() {
        Map<ReadEventBatchKey, ReadEventBatchRange> drained = new HashMap<>();

        for (Map.Entry<ReadEventBatchKey, ReadEventBatchRange> entry : pending.entrySet()) {
            ReadEventBatchKey key = entry.getKey();
            ReadEventBatchRange value = entry.getValue();

            if (pending.remove(key, value)) {
                drained.put(key, value);
            }
        }

        return drained;
    }
}
