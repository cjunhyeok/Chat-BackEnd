package com.chat.socket.interceptor;

import com.chat.utils.consts.SessionConst;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        if (!(request instanceof ServletServerHttpRequest)) {
            return false;
        }

        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        HttpSession httpSession = servletRequest.getSession(false);
        if (httpSession == null) {
            log.warn("WS 연결 거부: HTTP 세션 없음, remoteAddress={}", request.getRemoteAddress());
            return false;
        }

        Long loginMemberId = (Long) httpSession.getAttribute(SessionConst.SESSION_ID);
        if (loginMemberId == null) {
            log.warn("WS 연결 거부: 세션에 memberId 없음, httpSessionId={}", httpSession.getId());
            return false;
        }

        attributes.put(SessionConst.SESSION_ID, loginMemberId); // WebSocketSession의 attributes에 저장
        attributes.put(SessionConst.HTTP_SESSION_ID, httpSession.getId());

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
