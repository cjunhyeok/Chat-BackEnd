package com.chat.api;

import com.chat.exception.ErrorCode;
import com.chat.service.MessageService;
import com.chat.service.dtos.MessageHistory;
import com.chat.service.dtos.MessageHistoryResponse;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageApiController.class)
class MessageApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageService messageService;

    @Test
    @DisplayName("로그인 회원이 beforeChatId 없이 메시지 이력을 조회하면 null cursor로 Service에 전달하고 결과를 반환한다.")
    void 로그인_회원이_beforeChatId_없이_메시지_이력을_조회하면_null_cursor로_Service에_전달하고_결과를_반환한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long chatRoomId = 10L;
        Long lastReadMessageId = 50L;
        Long chatId = 100L;
        String message = "메시지";
        boolean hasMore = true;

        MessageHistory messageHistory = MessageHistory.builder()
                .chatId(chatId)
                .senderId(loginMemberId)
                .senderNickname("발신자")
                .message(message)
                .unreadMemberCount(0L)
                .createdDate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .discussionId(null)
                .discussionMessageCount(0)
                .build();

        MessageHistoryResponse response = new MessageHistoryResponse(lastReadMessageId, List.of(messageHistory), hasMore);

        given(messageService.findMessageHistory(chatRoomId, loginMemberId, null)).willReturn(response);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then: beforeChatId 없이 chatRoomId만 전달
        mockMvc.perform(get("/api/chats")
                        .param("chatRoomId", String.valueOf(chatRoomId))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastReadMessageId").value(lastReadMessageId))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].chatId").value(chatId))
                .andExpect(jsonPath("$.data.messages[0].message").value(message));

        verify(messageService, times(1)).findMessageHistory(chatRoomId, loginMemberId, null);
    }

    @Test
    @DisplayName("로그인 회원이 beforeChatId를 포함해 메시지 이력을 조회하면 cursor를 Service에 전달한다.")
    void 로그인_회원이_beforeChatId를_포함해_메시지_이력을_조회하면_cursor를_Service에_전달한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long chatRoomId = 10L;
        Long beforeChatId = 90L;

        MessageHistoryResponse response = new MessageHistoryResponse(null, List.of(), false);

        given(messageService.findMessageHistory(chatRoomId, loginMemberId, beforeChatId)).willReturn(response);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then: chatRoomId와 beforeChatId를 모두 전달
        mockMvc.perform(get("/api/chats")
                        .param("chatRoomId", String.valueOf(chatRoomId))
                        .param("beforeChatId", String.valueOf(beforeChatId))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages").isEmpty());

        verify(messageService, times(1)).findMessageHistory(chatRoomId, loginMemberId, beforeChatId);
    }

    @Test
    @DisplayName("필수 chatRoomId가 누락되면 400을 반환한다.")
    void 필수_chatRoomId가_누락되면_400을_반환한다() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, 1L);

        // when & then: chatRoomId 파라미터 없이 요청
        mockMvc.perform(get("/api/chats")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(ErrorCode.MISSING_REQUEST_PARAMETER.getStatus().name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.MISSING_REQUEST_PARAMETER.getErrorMessage()));

        verifyNoInteractions(messageService);
    }
}
