package com.chat.socket.handler;

import com.chat.service.dtos.chat.SendChat;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.BaseWebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationTextSocketHandler extends TextWebSocketHandler {

    private final WebsocketSessionManager websocketSessionManager;
    private final ChatRoomManager chatRoomManager;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
        websocketSessionManager.addSession(loginMemberId, session);

        log.info("Connect Websocket member : {}", loginMemberId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        BaseWebSocketMessage baseMessage = objectMapper.readValue(payload, BaseWebSocketMessage.class);

        switch (baseMessage.getMessageType()) {
            case CHAT_MESSAGE:
                SendChat sendChat = (SendChat) baseMessage;
                Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

                log.info("chat : {} member : {}", payload, loginMemberId);

                chatRoomManager.broadcastMessageToChatRoom(loginMemberId, sendChat.getChatRoomId(), payload);
                break;
            default:
                //todo 채팅 메시지 예외처리
                log.info("exception");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

        log.info("close Websocket member : {}", loginMemberId);

        websocketSessionManager.removeSession(loginMemberId);

        // todo 채팅방 세션 삭제 필요
        chatRoomManager.removeChatRoomsSessionBy(loginMemberId);
    }


}
