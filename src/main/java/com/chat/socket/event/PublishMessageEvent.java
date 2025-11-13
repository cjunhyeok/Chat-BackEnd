package com.chat.socket.event;

import com.chat.service.dtos.chat.SendChat;
import lombok.Getter;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

@Getter
public class PublishMessageEvent {

    private SendChat sendChat;
    private Set<WebSocketSession> sessions;

    public PublishMessageEvent(SendChat sendChat, Set<WebSocketSession> sessions) {
        this.sendChat = sendChat;
        this.sessions = sessions;
    }
}
