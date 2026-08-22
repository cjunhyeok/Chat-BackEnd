package com.chat.api;

import com.chat.api.request.member.JoinRequest;
import com.chat.api.request.member.LoginRequest;
import com.chat.api.response.member.GetMembersResponse;
import com.chat.service.MemberService;
import com.chat.service.dtos.LoginResponse;
import com.chat.utils.consts.SessionConst;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberApiController.class)
class MemberApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입 요청을 보내면 입력값을 Service에 전달하고 memberId를 data로 반환한다.")
    void 회원가입_요청을_보내면_입력값을_Service에_전달하고_memberId를_data로_반환한다() throws Exception {
        // given
        String username = "newUser";
        String password = "password1!";
        String nickname = "새회원";
        Long joinedMemberId = 10L;

        JoinRequest request = JoinRequest.builder()
                .username(username)
                .password(password)
                .nickname(nickname)
                .build();

        given(memberService.join(any(JoinRequest.class))).willReturn(joinedMemberId);

        // when & then: 회원가입은 공개 API이므로 세션 미첨부
        mockMvc.perform(post("/api/member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(joinedMemberId));

        ArgumentCaptor<JoinRequest> requestCaptor = ArgumentCaptor.forClass(JoinRequest.class);
        verify(memberService, times(1)).join(requestCaptor.capture());

        JoinRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getUsername()).isEqualTo(username);
        assertThat(capturedRequest.getPassword()).isEqualTo(password);
        assertThat(capturedRequest.getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("기존 세션이 없는 로그인 요청은 새 세션을 생성해 memberId를 저장한다.")
    void 기존_세션이_없는_로그인_요청은_새_세션을_생성해_memberId를_저장한다() throws Exception {
        // given
        String username = "firstLoginUser";
        String password = "password1!";
        Long loginMemberId = 20L;
        String nickname = "최초로그인회원";

        LoginResponse loginResponse = LoginResponse.builder()
                .memberId(loginMemberId)
                .nickname(nickname)
                .build();
        given(memberService.login(any(LoginRequest.class))).willReturn(loginResponse);

        LoginRequest request = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        // when: 기존 세션을 첨부하지 않은 최초 로그인 요청
        MvcResult result = mockMvc.perform(post("/api/member/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        HttpSession newSession = result.getRequest().getSession(false);
        assertThat(newSession).isNotNull();
        assertThat(newSession.getAttribute(SessionConst.SESSION_ID)).isEqualTo(loginMemberId);

        verify(memberService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("올바른 로그인 요청이면 기존 세션을 교체하고 새 세션에 memberId를 저장한다.")
    void 올바른_로그인_요청이면_기존_세션을_교체하고_새_세션에_memberId를_저장한다() throws Exception {
        // given
        String username = "loginUser";
        String password = "password1!";
        Long loginMemberId = 10L;
        String nickname = "로그인회원";

        LoginResponse loginResponse = LoginResponse.builder()
                .memberId(loginMemberId)
                .nickname(nickname)
                .build();
        given(memberService.login(any(LoginRequest.class))).willReturn(loginResponse);

        MockHttpSession existingSession = new MockHttpSession();
        existingSession.setAttribute(SessionConst.SESSION_ID, 999L);

        LoginRequest request = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        // when
        MvcResult result = mockMvc.perform(post("/api/member/login")
                        .session(existingSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(loginMemberId))
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andReturn();

        // then: Service 위임 검증
        ArgumentCaptor<LoginRequest> requestCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(memberService, times(1)).login(requestCaptor.capture());

        LoginRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getUsername()).isEqualTo(username);
        assertThat(capturedRequest.getPassword()).isEqualTo(password);

        // then: 세션 고정 방지 검증 - 요청에 첨부했던 기존 세션은 invalidate()로 무효화되고,
        // MockHttpServletRequest#getSession(boolean)이 invalid 상태를 감지해 새 MockHttpSession 인스턴스를 생성한다.
        assertThat(existingSession.isInvalid()).isTrue();

        HttpSession newSession = result.getRequest().getSession(false);
        assertThat(newSession).isNotNull();
        assertThat(newSession).isNotSameAs(existingSession);
        assertThat(newSession.getAttribute(SessionConst.SESSION_ID)).isEqualTo(loginMemberId);
    }

    @Test
    @DisplayName("로그인 회원이 회원 목록을 조회하면 Service 응답을 data로 반환한다.")
    void 로그인_회원이_회원_목록을_조회하면_Service_응답을_data로_반환한다() throws Exception {
        // given
        Long loginMemberId = 1L;

        GetMembersResponse first = GetMembersResponse.builder()
                .memberId(10L)
                .username("alice")
                .nickname("앨리스")
                .build();
        GetMembersResponse second = GetMembersResponse.builder()
                .memberId(20L)
                .username("bob")
                .nickname("밥")
                .build();

        given(memberService.findMembers()).willReturn(List.of(first, second));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(get("/api/members")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].memberId").value(10L))
                .andExpect(jsonPath("$.data[0].username").value("alice"))
                .andExpect(jsonPath("$.data[0].nickname").value("앨리스"))
                .andExpect(jsonPath("$.data[1].memberId").value(20L))
                .andExpect(jsonPath("$.data[1].username").value("bob"))
                .andExpect(jsonPath("$.data[1].nickname").value("밥"));

        verify(memberService, times(1)).findMembers();
    }

    @Test
    @DisplayName("로그인 회원이 닉네임을 변경하면 memberId와 새 닉네임을 Service에 전달한다.")
    void 로그인_회원이_닉네임을_변경하면_memberId와_새_닉네임을_Service에_전달한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        String newNickname = "새로운닉네임";

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(patch("/api/member/nickname")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + newNickname + "\"}"))
                .andExpect(status().isOk());

        verify(memberService, times(1)).changeNickname(loginMemberId, newNickname);
    }

    @Test
    @DisplayName("로그인 회원이 비밀번호를 변경하면 memberId와 현재·새 비밀번호를 Service에 전달한다.")
    void 로그인_회원이_비밀번호를_변경하면_memberId와_비밀번호들을_Service에_전달한다() throws Exception {
        // given
        Long loginMemberId = 1L;
        String currentPassword = "currentPassword1!";
        String newPassword = "newPassword1!";

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, loginMemberId);

        // when & then
        mockMvc.perform(patch("/api/member/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());

        verify(memberService, times(1)).changePassword(loginMemberId, currentPassword, newPassword);
    }
}
