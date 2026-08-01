package com.chat.socket.listener;

import com.chat.socket.event.PublishReadEvent;
import com.chat.socket.manager.ReadEventBatchAccumulator;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MessageBroadcastListenerTest {

    private SpaceManager spaceManager;
    private WebsocketSessionManager websocketSessionManager;
    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private ReadEventBatchAccumulator readEventBatchAccumulator;

    private MessageBroadcastListener messageBroadcastListener;

    @BeforeEach
    void init() {
        spaceManager = mock(SpaceManager.class);
        websocketSessionManager = mock(WebsocketSessionManager.class);
        objectMapper = mock(ObjectMapper.class);
        meterRegistry = mock(MeterRegistry.class);
        readEventBatchAccumulator = mock(ReadEventBatchAccumulator.class);

        messageBroadcastListener = new MessageBroadcastListener(
                spaceManager,
                websocketSessionManager,
                objectMapper,
                meterRegistry,
                readEventBatchAccumulator
        );
    }

    @Test
    @DisplayName("publishReadEventToSessions는 즉시 전송 없이 accumulator에 enqueue만 한다.")
    void publishReadEventToSessions는_accumulator에_enqueue만_한다() {
        PublishReadEvent event = new PublishReadEvent(10L, 1L, 100L, 110L);

        messageBroadcastListener.publishReadEventToSessions(event);

        verify(readEventBatchAccumulator).enqueue(event);
        verifyNoInteractions(spaceManager, websocketSessionManager, objectMapper);
    }
}
