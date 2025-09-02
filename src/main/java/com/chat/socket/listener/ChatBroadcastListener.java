package com.chat.socket.listener;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.service.dtos.chat.SendChat;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChatBroadcastListener {

    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageToSessions(SendChat sendChat, Set<WebSocketSession> sessions) {
        try {
            String sendChatMessage = objectMapper.writeValueAsString(sendChat);
            for (WebSocketSession session : sessions) {
                session.sendMessage(new TextMessage(sendChatMessage));
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.CHAT_ROOM_BROADCAST_IO_EXCEPTION);
        }
    }
}
