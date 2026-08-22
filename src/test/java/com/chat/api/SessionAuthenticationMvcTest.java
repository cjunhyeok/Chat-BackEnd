package com.chat.api;

import com.chat.exception.ErrorCode;
import com.chat.service.MemberService;
import com.chat.service.MessageService;
import com.chat.service.SpaceService;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SpaceApiController.class, MessageApiController.class, MemberApiController.class})
class SessionAuthenticationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpaceService spaceService;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private MemberService memberService;

    @Test
    @DisplayName("로그인 세션이 없으면 보호된 API는 401을 반환한다.")
    void 로그인_세션이_없으면_보호된_API는_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(ErrorCode.USER_NOT_AUTHENTICATED.getStatus().name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_AUTHENTICATED.getErrorMessage()));

        verifyNoInteractions(spaceService);
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

    @Test
    @DisplayName("로그인 세션이 없으면 회원 목록 조회는 401을 반환한다.")
    void 로그인_세션이_없으면_회원_목록_조회는_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(ErrorCode.USER_NOT_AUTHENTICATED.getStatus().name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_AUTHENTICATED.getErrorMessage()));

        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("로그인 세션이 없으면 닉네임 변경은 401을 반환한다.")
    void 로그인_세션이_없으면_닉네임_변경은_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/member/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"newNickname\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(ErrorCode.USER_NOT_AUTHENTICATED.getStatus().name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_AUTHENTICATED.getErrorMessage()));

        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("로그인 세션이 없으면 비밀번호 변경은 401을 반환한다.")
    void 로그인_세션이_없으면_비밀번호_변경은_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/member/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPassword1!\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(ErrorCode.USER_NOT_AUTHENTICATED.getStatus().name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_AUTHENTICATED.getErrorMessage()));

        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("로그인 세션이 없으면 초대 정보 조회는 401을 반환한다.")
    void 로그인_세션이_없으면_초대_정보_조회는_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/spaces/invite/{inviteCode}", "1234567890abcdef1234567890abcdef"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(ErrorCode.USER_NOT_AUTHENTICATED.getStatus().name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_AUTHENTICATED.getErrorMessage()));

        verify(spaceService, never()).findSpaceByInviteCode(any(), anyString());
    }

    @Test
    @DisplayName("로그인 세션이 없으면 초대 참여는 401을 반환한다.")
    void 로그인_세션이_없으면_초대_참여는_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/spaces/invite/{inviteCode}/join", "1234567890abcdef1234567890abcdef"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(ErrorCode.USER_NOT_AUTHENTICATED.getStatus().name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_AUTHENTICATED.getErrorMessage()));

        verify(spaceService, never()).joinSpaceByInviteCode(any(), anyString());
    }
}
