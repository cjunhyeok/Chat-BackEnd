package com.chat.api;

import com.chat.api.request.chatroom.SaveSpaceRequest;
import com.chat.api.response.chatroom.RenameSpaceResponse;
import com.chat.api.response.chatroom.SpaceInviteCodeResponse;
import com.chat.api.response.chatroom.SpaceInviteInfoResponse;
import com.chat.api.response.chatroom.SpaceMemberResponse;
import com.chat.api.response.chatroom.SpaceSummaryResponse;
import com.chat.service.SpaceService;
import com.chat.service.dtos.SaveSpaceDTO;
import com.chat.utils.consts.SessionConst;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpaceApiController.class)
class SpaceApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpaceService spaceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("로그인 회원이 Space를 생성하면 memberId와 title로 DTO를 조립해 Service에 전달한다.")
    void 로그인_회원이_Space를_생성하면_memberId와_title로_DTO를_조립해_Service에_전달한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        String title = "새로운 Space";
        Long createdChatRoomId = 10L;

        given(spaceService.saveSpace(any(SaveSpaceDTO.class))).willReturn(createdChatRoomId);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        SaveSpaceRequest request = new SaveSpaceRequest(title);

        // when & then
        mockMvc.perform(post("/api/chat/room")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chatRoomId").value(createdChatRoomId));

        ArgumentCaptor<SaveSpaceDTO> dtoCaptor = ArgumentCaptor.forClass(SaveSpaceDTO.class);
        verify(spaceService, times(1)).saveSpace(dtoCaptor.capture());

        SaveSpaceDTO capturedDto = dtoCaptor.getValue();
        assertThat(capturedDto.getSenderId()).isEqualTo(loginMemberId);
        assertThat(capturedDto.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("로그인 회원이 Space 목록을 조회하면 Service 응답을 data로 반환한다.")
    void 로그인_회원이_Space_목록을_조회하면_Service_응답을_data로_반환한다() throws Exception {
        // given
        Long loginMemberId = 1L;

        SpaceSummaryResponse first = SpaceSummaryResponse.builder()
                .chatRoomId(10L)
                .title("첫번째 Space")
                .lastMessage("안녕하세요")
                .lastChatId(100L)
                .unreadMessageCount(3L)
                .createdDate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        SpaceSummaryResponse second = SpaceSummaryResponse.builder()
                .chatRoomId(20L)
                .title("두번째 Space")
                .lastMessage("반갑습니다")
                .lastChatId(200L)
                .unreadMessageCount(0L)
                .createdDate(LocalDateTime.of(2026, 2, 1, 0, 0))
                .build();

        given(spaceService.findSpaces(loginMemberId)).willReturn(List.of(first, second));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(get("/api/chat/rooms")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].chatRoomId").value(10L))
                .andExpect(jsonPath("$.data[0].title").value("첫번째 Space"))
                .andExpect(jsonPath("$.data[0].unreadMessageCount").value(3L))
                .andExpect(jsonPath("$.data[1].chatRoomId").value(20L))
                .andExpect(jsonPath("$.data[1].title").value("두번째 Space"))
                .andExpect(jsonPath("$.data[1].unreadMessageCount").value(0L));

        verify(spaceService, times(1)).findSpaces(loginMemberId);
    }

    @Test
    @DisplayName("로그인 회원이 Space에서 나가면 memberId와 chatRoomId를 Service에 전달한다.")
    void 로그인_회원이_Space에서_나가면_memberId와_chatRoomId를_Service에_전달한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long chatRoomId = 10L;

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(delete("/api/chat/room/{chatRoomId}", chatRoomId)
                        .session(session))
                .andExpect(status().isOk());

        verify(spaceService, times(1)).leaveSpace(loginMemberId, chatRoomId);
    }

    @Test
    @DisplayName("로그인 회원이 Space 이름을 변경하면 입력값을 Service에 전달하고 응답을 반환한다.")
    void 로그인_회원이_Space_이름을_변경하면_입력값을_Service에_전달하고_응답을_반환한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long chatRoomId = 10L;
        String newTitle = "새로운 Space";

        given(spaceService.renameSpace(loginMemberId, chatRoomId, newTitle))
                .willReturn(new RenameSpaceResponse(chatRoomId, newTitle));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(patch("/api/chat/room/{chatRoomId}", chatRoomId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + newTitle + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chatRoomId").value(chatRoomId))
                .andExpect(jsonPath("$.data.title").value(newTitle));

        verify(spaceService, times(1)).renameSpace(loginMemberId, chatRoomId, newTitle);
    }

    @Test
    @DisplayName("로그인 회원이 Space 참여자를 조회하면 memberId와 chatRoomId를 Service에 전달하고 목록을 반환한다.")
    void 로그인_회원이_Space_참여자를_조회하면_memberId와_chatRoomId를_Service에_전달하고_목록을_반환한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long chatRoomId = 10L;

        SpaceMemberResponse first = new SpaceMemberResponse(100L, "앨리스");
        SpaceMemberResponse second = new SpaceMemberResponse(200L, "밥");

        given(spaceService.findSpaceMembers(loginMemberId, chatRoomId)).willReturn(List.of(first, second));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(get("/api/chat/room/{chatRoomId}/members", chatRoomId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].memberId").value(100L))
                .andExpect(jsonPath("$.data[0].nickname").value("앨리스"))
                .andExpect(jsonPath("$.data[1].memberId").value(200L))
                .andExpect(jsonPath("$.data[1].nickname").value("밥"));

        verify(spaceService, times(1)).findSpaceMembers(loginMemberId, chatRoomId);
    }

    @Test
    @DisplayName("로그인 회원이 Space에 참여자를 초대하면 memberId와 chatRoomId와 memberIds를 Service에 전달한다.")
    void 로그인_회원이_Space에_참여자를_초대하면_memberId와_chatRoomId와_memberIds를_Service에_전달한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long chatRoomId = 10L;
        Set<Long> memberIds = Set.of(100L, 200L);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(post("/api/chat/room/{chatRoomId}/members", chatRoomId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[100,200]}"))
                .andExpect(status().isOk());

        verify(spaceService, times(1)).inviteMembers(loginMemberId, chatRoomId, memberIds);
    }

    @Test
    @DisplayName("로그인 회원이 Space 초대 코드를 조회하면 memberId와 spaceId를 Service에 전달하고 inviteCode를 반환한다.")
    void 로그인_회원이_Space_초대_코드를_조회하면_memberId와_spaceId를_Service에_전달하고_inviteCode를_반환한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long spaceId = 10L;
        String inviteCode = "1234567890abcdef1234567890abcdef";

        given(spaceService.getSpaceInviteCode(loginMemberId, spaceId))
                .willReturn(new SpaceInviteCodeResponse(inviteCode));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(get("/api/spaces/{spaceId}/invite-code", spaceId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteCode").value(inviteCode));

        verify(spaceService, times(1)).getSpaceInviteCode(loginMemberId, spaceId);
    }

    @Test
    @DisplayName("로그인 회원이 초대 정보를 조회하면 memberId와 inviteCode를 Service에 전달하고 Space 정보를 반환한다.")
    void 로그인_회원이_초대_정보를_조회하면_memberId와_inviteCode를_Service에_전달하고_Space_정보를_반환한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long spaceId = 10L;
        String title = "초대받은 Space";
        long memberCount = 3L;
        boolean alreadyJoined = true;
        String inviteCode = "1234567890abcdef1234567890abcdef";

        given(spaceService.findSpaceByInviteCode(loginMemberId, inviteCode))
                .willReturn(new SpaceInviteInfoResponse(spaceId, title, memberCount, alreadyJoined));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(get("/api/spaces/invite/{inviteCode}", inviteCode)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spaceId").value(spaceId))
                .andExpect(jsonPath("$.data.title").value(title))
                .andExpect(jsonPath("$.data.memberCount").value(memberCount))
                .andExpect(jsonPath("$.data.alreadyJoined").value(true));

        verify(spaceService, times(1)).findSpaceByInviteCode(loginMemberId, inviteCode);
    }

    @Test
    @DisplayName("로그인 회원이 초대 코드로 Space에 참여하면 memberId와 inviteCode를 Service에 전달하고 참여 결과를 반환한다.")
    void 로그인_회원이_초대_코드로_Space에_참여하면_memberId와_inviteCode를_Service에_전달하고_참여_결과를_반환한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        Long spaceId = 10L;
        String inviteCode = "1234567890abcdef1234567890abcdef";

        given(spaceService.joinSpaceByInviteCode(loginMemberId, inviteCode)).willReturn(spaceId);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(post("/api/spaces/invite/{inviteCode}/join", inviteCode)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spaceId").value(spaceId));

        verify(spaceService, times(1)).joinSpaceByInviteCode(loginMemberId, inviteCode);
    }
}
