package com.chat.socket.manager;

import com.chat.socket.event.PublishReadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ReadEventBatchAccumulatorTest {

    private ReadEventBatchAccumulator accumulator;

    @BeforeEach
    void init() {
        accumulator = new ReadEventBatchAccumulator();
    }

    @Test
    @DisplayName("같은 key의 연속된 읽음 범위는 하나로 병합된다.")
    void 같은_key의_연속된_읽음_범위는_병합된다() {
        accumulator.enqueue(new PublishReadEvent(10L, 1L, 100L, 105L));
        accumulator.enqueue(new PublishReadEvent(10L, 1L, 105L, 110L));

        Map<ReadEventBatchKey, ReadEventBatchRange> drained = accumulator.drain();

        ReadEventBatchRange range = drained.get(new ReadEventBatchKey(1L, 10L));
        assertThat(range.getPreviousLastReadChatId()).isEqualTo(100L);
        assertThat(range.getCurrentLastReadChatId()).isEqualTo(110L);
    }

    @Test
    @DisplayName("previous 중 하나라도 null이면 병합된 previous도 null이다.")
    void previous가_하나라도_null이면_병합_결과도_null이다() {
        accumulator.enqueue(new PublishReadEvent(10L, 1L, null, 105L));
        accumulator.enqueue(new PublishReadEvent(10L, 1L, 105L, 110L));

        Map<ReadEventBatchKey, ReadEventBatchRange> drained = accumulator.drain();

        ReadEventBatchRange range = drained.get(new ReadEventBatchKey(1L, 10L));
        assertThat(range.getPreviousLastReadChatId()).isNull();
        assertThat(range.getCurrentLastReadChatId()).isEqualTo(110L);
    }

    @Test
    @DisplayName("병합된 current는 가장 큰 값을 유지한다.")
    void current는_가장_큰_값을_유지한다() {
        accumulator.enqueue(new PublishReadEvent(10L, 1L, 100L, 120L));
        accumulator.enqueue(new PublishReadEvent(10L, 1L, 100L, 110L));

        Map<ReadEventBatchKey, ReadEventBatchRange> drained = accumulator.drain();

        ReadEventBatchRange range = drained.get(new ReadEventBatchKey(1L, 10L));
        assertThat(range.getCurrentLastReadChatId()).isEqualTo(120L);
    }

    @Test
    @DisplayName("같은 chatRoomId여도 memberId가 다르면 별도 항목으로 유지된다.")
    void 다른_memberId는_별도_항목이다() {
        accumulator.enqueue(new PublishReadEvent(10L, 1L, 100L, 110L));
        accumulator.enqueue(new PublishReadEvent(20L, 1L, 200L, 210L));

        Map<ReadEventBatchKey, ReadEventBatchRange> drained = accumulator.drain();

        assertThat(drained).hasSize(2);
        assertThat(drained.get(new ReadEventBatchKey(1L, 10L)).getCurrentLastReadChatId()).isEqualTo(110L);
        assertThat(drained.get(new ReadEventBatchKey(1L, 20L)).getCurrentLastReadChatId()).isEqualTo(210L);
    }

    @Test
    @DisplayName("같은 memberId여도 chatRoomId가 다르면 별도 항목으로 유지된다.")
    void 다른_chatRoomId는_별도_항목이다() {
        accumulator.enqueue(new PublishReadEvent(10L, 1L, 100L, 110L));
        accumulator.enqueue(new PublishReadEvent(10L, 2L, 300L, 310L));

        Map<ReadEventBatchKey, ReadEventBatchRange> drained = accumulator.drain();

        assertThat(drained).hasSize(2);
        assertThat(drained.get(new ReadEventBatchKey(1L, 10L)).getCurrentLastReadChatId()).isEqualTo(110L);
        assertThat(drained.get(new ReadEventBatchKey(2L, 10L)).getCurrentLastReadChatId()).isEqualTo(310L);
    }

    @Test
    @DisplayName("아무 것도 쌓이지 않은 상태에서 drain하면 빈 결과를 반환한다.")
    void 빈_상태에서_drain하면_빈_결과를_반환한다() {
        Map<ReadEventBatchKey, ReadEventBatchRange> drained = accumulator.drain();

        assertThat(drained).isEmpty();
    }

    @Test
    @DisplayName("drain된 항목은 accumulator에서 제거된다.")
    void drain된_항목은_제거된다() {
        accumulator.enqueue(new PublishReadEvent(10L, 1L, 100L, 110L));

        accumulator.drain();
        Map<ReadEventBatchKey, ReadEventBatchRange> secondDrain = accumulator.drain();

        assertThat(secondDrain).isEmpty();
    }

    @Test
    @DisplayName("drain이 진행되는 동안 같은 key가 갱신되어도 최신 값은 유실되지 않는다.")
    void drain_중_같은_key가_갱신되어도_최신값이_유실되지_않는다() throws InterruptedException {
        Long chatRoomId = 1L;
        Long memberId = 10L;
        int updateCount = 500;

        List<ReadEventBatchRange> observed = new CopyOnWriteArrayList<>();

        accumulator.enqueue(new PublishReadEvent(memberId, chatRoomId, 0L, 1L));

        Thread updater = new Thread(() -> {
            for (int current = 2; current <= updateCount; current++) {
                accumulator.enqueue(new PublishReadEvent(memberId, chatRoomId, (long) (current - 1), (long) current));
            }
        });

        updater.start();
        while (updater.isAlive()) {
            ReadEventBatchRange range = accumulator.drain().get(new ReadEventBatchKey(chatRoomId, memberId));
            if (range != null) {
                observed.add(range);
            }
        }
        updater.join();

        ReadEventBatchRange finalRange = accumulator.drain().get(new ReadEventBatchKey(chatRoomId, memberId));
        if (finalRange != null) {
            observed.add(finalRange);
        }

        long maxObservedCurrent = observed.stream()
                .mapToLong(ReadEventBatchRange::getCurrentLastReadChatId)
                .max()
                .orElseThrow();

        assertThat(maxObservedCurrent).isEqualTo(updateCount);
    }
}
