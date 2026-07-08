package com.chat.utils.consts;

public final class MessageMetricNames {

    public static final String MESSAGE_FLOW_DURATION = "message_flow_duration_seconds";
    public static final String MESSAGE_IDEMPOTENCY_CHECK_DURATION = "message_idempotency_check_duration_seconds";
    public static final String MESSAGE_CURSOR_UPDATE_DURATION = "message_cursor_update_duration_seconds";
    public static final String MESSAGE_DATA_LOAD_DURATION = "message_data_load_duration_seconds";
    public static final String MESSAGE_UPDATE_BUILD_DURATION = "message_update_build_duration_seconds";
    public static final String MESSAGE_AFTER_COMMIT_DELAY = "message_after_commit_delay_seconds";
    public static final String MESSAGE_BROADCAST_CHAT_DURATION = "message_broadcast_chat_duration_seconds";
    public static final String MESSAGE_BROADCAST_UPDATE_DURATION = "message_broadcast_update_duration_seconds";

    public static final String READ_UP_TO_DURATION = "read_up_to_duration_seconds";
    public static final String READ_UPDATE_CHAT_ROOM_BUILD_DURATION = "read_update_chat_room_build_duration_seconds";
    public static final String READ_EVENT_BROADCAST_DURATION = "read_event_broadcast_duration_seconds";
    public static final String READ_UPDATE_CHAT_ROOM_SEND_DURATION = "read_update_chat_room_send_duration_seconds";
    public static final String BROADCAST_EXECUTOR_QUEUE_WAIT_DURATION = "broadcast_executor_queue_wait_seconds";
    public static final String BROADCAST_EXECUTOR_QUEUE_SIZE = "broadcast_executor_queue_size";
    public static final String BROADCAST_EXECUTOR_ACTIVE_THREADS = "broadcast_executor_active_threads";
    public static final String BROADCAST_EXECUTOR_CALLER_RUNS_TOTAL = "broadcast_executor_caller_runs_total";

    public static final String READ_UPDATE_SPACE_LOAD_DURATION = "read_update_space_load_duration_seconds";
    public static final String READ_UPDATE_LAST_MESSAGE_LOAD_DURATION = "read_update_last_message_load_duration_seconds";
    public static final String READ_UPDATE_UNREAD_COUNT_LOAD_DURATION = "read_update_unread_count_load_duration_seconds";

    private MessageMetricNames() {}
}
