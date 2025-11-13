package com.chat.api;

import com.chat.api.response.chatroom.ChatRoomResponse;
import com.chat.api.response.chatroom.ChatRoomsResponse;
import com.chat.api.request.chatroom.SaveChatRooomRequest;
import com.chat.utils.consts.SessionConst;
import com.chat.service.ChatRoomService;
import com.chat.service.dtos.SaveChatRoomDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatRoomApiController {

    private final ChatRoomService chatRoomService;

    @PostMapping("/api/chat/room")
    public Result<ChatRoomResponse> chatRoom(@RequestBody SaveChatRooomRequest request,
                                             @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        SaveChatRoomDTO saveChatRooomDto = SaveChatRoomDTO
                .builder()
                .senderId(loginMemberId)
                .receiverIds(request.getReceiverIds())
                .title(request.getTitle())
                .build();
        Long chatRoomId = chatRoomService.saveChatRoom(saveChatRooomDto);

        return Result
                .<ChatRoomResponse>builder()
                .data(new ChatRoomResponse(chatRoomId))
                .status(HttpStatus.OK)
                .message("채팅방 생성이 완료됐습니다.")
                .build();
    }

    @GetMapping("/api/chat/rooms")
    public Result<List<ChatRoomsResponse>> chtaRooms(@SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        List<ChatRoomsResponse> chatRooms = chatRoomService.findChatRooms(loginMemberId);

        return Result
                .<List<ChatRoomsResponse>>builder()
                .data(chatRooms)
                .status(HttpStatus.OK)
                .message("채팅방 목록 조회가 완료됐습니다.")
                .build();
    }
}
