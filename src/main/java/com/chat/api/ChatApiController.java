package com.chat.api;

import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.SessionConst;
import com.chat.service.ChatService;
import com.chat.service.dtos.ChatHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;
    private final WebsocketSessionManager websocketSessionManager;
    private final ChatRoomManager chatRoomManager;

    @GetMapping("/api/chats")
    public Result<List<ChatHistory>> chatHistory(@RequestParam("chatRoomId") Long chatRoomId,
                                                 @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) throws IOException {

        // 채팅 내역 조회
        List<ChatHistory> chatHistory = chatService.findChatHistory(chatRoomId, loginMemberId);

        // 채팅 소켓 연결
        WebSocketSession webSocketSession = websocketSessionManager.getSessionBy(loginMemberId);
        chatRoomManager.addSessionToRoom(chatRoomId, webSocketSession);

        return Result
                .<List<ChatHistory> >builder()
                .data(chatHistory)
                .status(HttpStatus.OK)
                .message("채팅 메시지 조회에 성공했습니다.")
                .build();
    }
}
