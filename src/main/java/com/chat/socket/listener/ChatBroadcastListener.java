package com.chat.socket.listener;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.socket.event.PublishMessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ChatBroadcastListener {

    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageToSessions(PublishMessageEvent event) {
        try {
            String sendChatMessage = objectMapper.writeValueAsString(event.getSendChat());
            for (WebSocketSession session : event.getSessions()) {
                session.sendMessage(new TextMessage(sendChatMessage));
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.CHAT_ROOM_BROADCAST_IO_EXCEPTION);
        }
    }
}
