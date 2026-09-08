package com.chat.socket.interceptor;

import com.chat.utils.consts.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ChatHandshakeInterceptorTest {

    private ChatHandshakeInterceptor chatHandshakeInterceptor;

    @BeforeEach
    void setUp() {
        chatHandshakeInterceptor = new ChatHandshakeInterceptor();
    }

    @Test
    @DisplayName("로그인 memberId가 있는 HTTP session이면 handshake를 허용하고 attributes에 인증 정보를 저장한다.")
    void 로그인_memberId가_있는_HTTP_session이면_handshake를_허용하고_attributes에_인증_정보를_저장한다() throws Exception {
        // given
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        HttpSession httpSession = mockRequest.getSession(true);
        Long memberId = 1L;
        httpSession.setAttribute(SessionConst.SESSION_ID, memberId);

        ServletServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        // when
        boolean result = chatHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);

        // then
        assertThat(result).isTrue();
        assertThat(attributes.get(SessionConst.SESSION_ID)).isEqualTo(memberId);
        assertThat(attributes.get(SessionConst.SESSION_ID)).isInstanceOf(Long.class);
        assertThat(attributes.get(SessionConst.HTTP_SESSION_ID)).isEqualTo(httpSession.getId());
    }

    @Test
    @DisplayName("HTTP session이 없으면 handshake를 거부한다.")
    void HTTP_session이_없으면_handshake를_거부한다() throws Exception {
        // given
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();

        ServletServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        // when
        boolean result = chatHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);

        // then
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("HTTP session은 있지만 로그인 memberId가 없으면 handshake를 거부한다.")
    void HTTP_session은_있지만_로그인_memberId가_없으면_handshake를_거부한다() throws Exception {
        // given
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.getSession(true);

        ServletServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        // when
        boolean result = chatHandshakeInterceptor.beforeHandshake(request, response, wsHandler, attributes);

        // then
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
    }
}
