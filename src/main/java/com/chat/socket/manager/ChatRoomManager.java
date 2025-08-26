package com.chat.socket.manager;

import com.chat.service.ChatReadService;
import com.chat.service.ChatRoomParticipantService;
import com.chat.service.ChatRoomService;
import com.chat.service.ChatService;
import com.chat.service.dtos.LastChatRead;
import com.chat.service.dtos.SaveChatData;
import com.chat.service.dtos.chat.EnterChatRoom;
import com.chat.service.dtos.chat.SendChat;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatRoomManager {

    private final Map<Long, Set<WebSocketSession>> chatRooms = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> memberToRoomsMap = new ConcurrentHashMap<>();
    private final ChatRoomService chatRoomService;
    private final ChatService chatService;
    private final ChatRoomParticipantService chatRoomParticipantService;
    private final ChatReadService chatReadService;
    private final ObjectMapper objectMapper;

    public void addSessionToRoom(Long chatRoomId, WebSocketSession session) throws IOException {

        chatRoomService.validChatRoomId(chatRoomId);

        Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
        chatRoomParticipantService.enterChatRoom(chatRoomId, loginMemberId);

        chatRooms.computeIfAbsent(chatRoomId, key -> ConcurrentHashMap.newKeySet()).add(session);
        memberToRoomsMap.computeIfAbsent(loginMemberId, k -> ConcurrentHashMap.newKeySet()).add(chatRoomId);

        broadcastEnterChatRoom(loginMemberId, chatRoomId);
    }

    public void broadcastEnterChatRoom(Long loginMemberId, Long chatRoomId) throws IOException {

        LastChatRead lastChatRead = chatReadService.findLastChatBy(loginMemberId, chatRoomId);
        EnterChatRoom enterChatRoom = EnterChatRoom.builder()
                .messageType(MessageType.CHAT_ENTER)
                .lastReadChatId(lastChatRead != null ? lastChatRead.getLastChatReadId() : null)
                .memberId(lastChatRead != null ? lastChatRead.getMemberId() : null)
                .build();
        String enterChatRoomMessage = objectMapper.writeValueAsString(enterChatRoom);

        Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);

        for (WebSocketSession session : sessions) {
            session.sendMessage(new TextMessage(enterChatRoomMessage));
        }
    }

    public void removeChatRoomsSessionBy(Long memberId) {
        Set<Long> chatRoomIds = memberToRoomsMap.get(memberId);

        if (chatRoomIds == null || chatRoomIds.isEmpty()) {
            return;
        }

        for (Long chatRoomId : chatRoomIds) {
            chatRoomParticipantService.leaveChatRoom(chatRoomId, memberId);

            Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);
            sessions.removeIf(session -> {
                Object userIdObj = session.getAttributes().get(SessionConst.SESSION_ID);
                if (userIdObj instanceof Long) {
                    return ((Long) userIdObj).equals(memberId);
                }
                return false;
            });
        }
    }

    public void broadcastMessageToChatRoom(Long senderId, Long chatRoomId, String message) throws IOException {

        Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);
        if (sessions == null || sessions.isEmpty()) {
            //todo 예외처리
            return ;
        }

        SendChat sendChat = objectMapper.readValue(message, SendChat.class);

        Long savedChatId = chatService.saveChat(senderId, chatRoomId, sendChat.getMessage());
        SaveChatData chatData = chatService.findChatData(savedChatId);

        sendChat.updateSavedChat(chatData);
        String sendChatMessage = objectMapper.writeValueAsString(sendChat);

        for (WebSocketSession session : sessions) {
            session.sendMessage(new TextMessage(sendChatMessage));
        }
    }
}
