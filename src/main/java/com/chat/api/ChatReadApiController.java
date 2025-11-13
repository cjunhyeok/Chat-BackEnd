package com.chat.api;

import com.chat.utils.consts.SessionConst;
import com.chat.service.ChatReadService;
import com.chat.service.dtos.LastChatRead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatReadApiController {

    private final ChatReadService chatReadService;

    @GetMapping("/api/chat/reads")
    public Result<List<LastChatRead>> lastChatReads(@RequestParam("chatRoomId") Long chatRoomId,
                                                    @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        List<LastChatRead> lastChatReads = chatReadService.findMembersChatReadIn(chatRoomId);

        return Result
                .<List<LastChatRead>>builder()
                .data(lastChatReads)
                .status(HttpStatus.OK)
                .message("마지막 채팅 조회에 성공했습니다.")
                .build();
    }
}
