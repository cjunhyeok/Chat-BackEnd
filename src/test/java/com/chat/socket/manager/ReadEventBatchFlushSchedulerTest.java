package com.chat.socket.manager;

import com.chat.service.dtos.chat.ReadEventBatch;
import com.chat.service.dtos.chat.ReadEventBatchItem;
import com.chat.socket.listener.MessageBroadcastListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ReadEventBatchFlushSchedulerTest {

    private ReadEventBatchAccumulator readEventBatchAccumulator;
    private MessageBroadcastListener messageBroadcastListener;
    private ReadEventBatchFlushScheduler flushScheduler;

    @BeforeEach
    void init() {
        readEventBatchAccumulator = mock(ReadEventBatchAccumulator.class);
        messageBroadcastListener = mock(MessageBroadcastListener.class);
        flushScheduler = new ReadEventBatchFlushScheduler(readEventBatchAccumulator, messageBroadcastListener);
    }

    @Test
    @DisplayName("drain 결과가 비어 있으면 전송하지 않는다.")
    void drain_결과가_비어있으면_전송하지_않는다() {
        given(readEventBatchAccumulator.drain()).willReturn(new HashMap<>());

        flushScheduler.flush();

        verifyNoInteractions(messageBroadcastListener);
    }

    @Test
    @DisplayName("같은 방의 여러 회원은 batch 하나로 묶인다.")
    void 같은_방의_여러_회원은_batch_하나로_묶인다() {
        Long chatRoomId = 1L;
        Map<ReadEventBatchKey, ReadEventBatchRange> drained = new LinkedHashMap<>();
        drained.put(new ReadEventBatchKey(chatRoomId, 10L), new ReadEventBatchRange(100L, 110L));
        drained.put(new ReadEventBatchKey(chatRoomId, 20L), new ReadEventBatchRange(200L, 210L));
        given(readEventBatchAccumulator.drain()).willReturn(drained);

        flushScheduler.flush();

        ArgumentCaptor<ReadEventBatch> captor = ArgumentCaptor.forClass(ReadEventBatch.class);
        verify(messageBroadcastListener, times(1)).broadcastReadEventBatch(captor.capture());

        ReadEventBatch batch = captor.getValue();
        assertThat(batch.getChatRoomId()).isEqualTo(chatRoomId);
        assertThat(batch.getReads()).extracting(ReadEventBatchItem::getMemberId)
                .containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("다른 방은 각각 별도 batch로 전송된다.")
    void 다른_방은_각각_별도_batch로_전송된다() {
        Map<ReadEventBatchKey, ReadEventBatchRange> drained = new LinkedHashMap<>();
        drained.put(new ReadEventBatchKey(1L, 10L), new ReadEventBatchRange(100L, 110L));
        drained.put(new ReadEventBatchKey(2L, 10L), new ReadEventBatchRange(300L, 310L));
        given(readEventBatchAccumulator.drain()).willReturn(drained);

        flushScheduler.flush();

        ArgumentCaptor<ReadEventBatch> captor = ArgumentCaptor.forClass(ReadEventBatch.class);
        verify(messageBroadcastListener, times(2)).broadcastReadEventBatch(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(ReadEventBatch::getChatRoomId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("item의 memberId/previousLastReadChatId/currentLastReadChatId가 정확히 매핑된다.")
    void item_필드가_정확히_매핑된다() {
        Long chatRoomId = 1L;
        Long memberId = 10L;
        Map<ReadEventBatchKey, ReadEventBatchRange> drained = new LinkedHashMap<>();
        drained.put(new ReadEventBatchKey(chatRoomId, memberId), new ReadEventBatchRange(100L, 110L));
        given(readEventBatchAccumulator.drain()).willReturn(drained);

        flushScheduler.flush();

        ArgumentCaptor<ReadEventBatch> captor = ArgumentCaptor.forClass(ReadEventBatch.class);
        verify(messageBroadcastListener).broadcastReadEventBatch(captor.capture());

        List<ReadEventBatchItem> reads = captor.getValue().getReads();
        assertThat(reads).hasSize(1);
        ReadEventBatchItem item = reads.get(0);
        assertThat(item.getMemberId()).isEqualTo(memberId);
        assertThat(item.getPreviousLastReadChatId()).isEqualTo(100L);
        assertThat(item.getCurrentLastReadChatId()).isEqualTo(110L);
    }

    @Test
    @DisplayName("한 방의 전송 요청이 실패해도 다른 방 처리는 계속된다.")
    void 한_방의_전송_요청이_실패해도_다른_방_처리는_계속된다() {
        Map<ReadEventBatchKey, ReadEventBatchRange> drained = new LinkedHashMap<>();
        drained.put(new ReadEventBatchKey(1L, 10L), new ReadEventBatchRange(100L, 110L));
        drained.put(new ReadEventBatchKey(2L, 20L), new ReadEventBatchRange(200L, 210L));
        given(readEventBatchAccumulator.drain()).willReturn(drained);

        willThrow(new RuntimeException("전송 실패"))
                .given(messageBroadcastListener)
                .broadcastReadEventBatch(argThatChatRoomIdIs(1L));

        flushScheduler.flush();

        ArgumentCaptor<ReadEventBatch> captor = ArgumentCaptor.forClass(ReadEventBatch.class);
        verify(messageBroadcastListener, times(2)).broadcastReadEventBatch(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ReadEventBatch::getChatRoomId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("flush 한 번당 drain은 한 번만 호출된다.")
    void flush_한_번당_drain은_한_번만_호출된다() {
        given(readEventBatchAccumulator.drain()).willReturn(new HashMap<>());

        flushScheduler.flush();

        verify(readEventBatchAccumulator, times(1)).drain();
    }

    private ReadEventBatch argThatChatRoomIdIs(Long chatRoomId) {
        return org.mockito.ArgumentMatchers.argThat(batch -> batch.getChatRoomId().equals(chatRoomId));
    }
}
