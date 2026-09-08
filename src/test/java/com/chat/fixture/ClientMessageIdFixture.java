package com.chat.fixture;

import java.util.concurrent.atomic.AtomicLong;

public final class ClientMessageIdFixture {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private ClientMessageIdFixture() {
    }

    public static String nextClientMessageId() {
        return "test-client-message-id-" + SEQUENCE.incrementAndGet();
    }
}
