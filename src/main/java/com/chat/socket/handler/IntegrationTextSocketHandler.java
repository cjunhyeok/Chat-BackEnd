package com.chat.socket.handler;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.service.DiscussionMessageService;
import com.chat.service.SpaceService;
import com.chat.service.MessageService;
import com.chat.service.MemberService;
import com.chat.service.dtos.chat.EnterRoomAckResponse;
import com.chat.service.dtos.chat.EnterRoomRequest;
import com.chat.service.dtos.chat.ErrorResponse;
import com.chat.service.dtos.chat.ReadUpToRequest;
import com.chat.service.dtos.chat.RoomActiveRequest;
import com.chat.service.dtos.chat.RoomInactiveRequest;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.SendDiscussionMessage;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.consts.WsMetricNames;
import com.chat.utils.message.BaseWebSocketMessage;
import com.chat.utils.message.MessageType;
import com.chat.utils.valid.IdValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationTextSocketHandler extends TextWebSocketHandler {

    private final WebsocketSessionManager websocketSessionManager;
    private final SpaceManager spaceManager;
    private final SpaceService spaceService;
    private final MessageService messageService;
    private final MemberService memberService;
    private final DiscussionMessageService discussionMessageService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object sessionObject = session.getAttributes().get(SessionConst.SESSION_ID);

        if (sessionObject == null) {
            log.warn("WS 처리 실패: SESSION_ID 없음, session={}", session.getId());
            throw new CustomException(ErrorCode.WEB_SOCKET_SESSION_NOT_EXIST);
        }

        Long loginMemberId = (Long) sessionObject;
        websocketSessionManager.addSession(loginMemberId, session);
        spaceManager.registerSession(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Long memberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

        BaseWebSocketMessage baseMessage;
        try {
            baseMessage = objectMapper.readValue(payload, BaseWebSocketMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("WS 처리 실패: 메시지 파싱 오류, session={}, memberId={}", session.getId(), memberId, e);
            sendError(session, null, null, null, ErrorCode.INVALID_MESSAGE_FORMAT);
            return;
        }

        try {
            switch (baseMessage.getMessageType()) {
                case CHAT_MESSAGE:
                    handleChatMessage(session, memberId, (SendChat) baseMessage);
                    break;
                case ENTER_ROOM:
                    handleEnterRoom(session, memberId, (EnterRoomRequest) baseMessage);
                    break;
                case ROOM_ACTIVE:
                    handleRoomActive(session, memberId, (RoomActiveRequest) baseMessage);
                    break;
                case ROOM_INACTIVE:
                    handleRoomInactive(session, (RoomInactiveRequest) baseMessage);
                    break;
                case READ_UP_TO:
                    handleReadUpTo(session, memberId, (ReadUpToRequest) baseMessage);
                    break;
                case DISCUSSION_MESSAGE:
                    handleDiscussionMessage(memberId, (SendDiscussionMessage) baseMessage);
                    break;
                default:
                    log.warn("WS 처리 실패: 알 수 없는 messageType, session={}, memberId={}, messageType={}",
                            session.getId(), memberId, baseMessage.getMessageType());
                    sendError(session, baseMessage.getMessageType(), null, null, ErrorCode.UNKNOWN_MESSAGE_TYPE);
            }
        } catch (CustomException e) {
            log.warn("WS 처리 실패: session={}, memberId={}, messageType={}, chatRoomId={}, errorCode={}",
                    session.getId(), memberId, baseMessage.getMessageType(), extractChatRoomId(baseMessage), e.getErrorCode(), e);
            sendError(session, baseMessage.getMessageType(), extractChatRoomId(baseMessage),
                    extractClientMessageId(baseMessage), e.getErrorCode());
        } catch (Exception e) {
            log.error("WS 처리 실패: 예상치 못한 오류, session={}, memberId={}, messageType={}, chatRoomId={}",
                    session.getId(), memberId, baseMessage.getMessageType(), extractChatRoomId(baseMessage), e);
            sendError(session, baseMessage.getMessageType(), extractChatRoomId(baseMessage),
                    extractClientMessageId(baseMessage), ErrorCode.UNEXPECTED_ERROR);
        }
    }

    private void handleChatMessage(WebSocketSession session, Long memberId, SendChat sendChat) {
        Long chatRoomId = sendChat.getChatRoomId();

        if (!isSessionInRoom(session, chatRoomId)) {
            log.warn("WS 처리 실패: 미참여 방에 메시지 전송, session={}, memberId={}, chatRoomId={}, messageType={}",
                    session.getId(), memberId, chatRoomId, sendChat.getMessageType());
            sendError(session, sendChat.getMessageType(), chatRoomId,
                    sendChat.getClientMessageId(), ErrorCode.ROOM_NOT_JOINED);
            return;
        }

        log.debug("CHAT_MESSAGE 수신: memberId={}, chatRoomId={}", memberId, chatRoomId);

        spaceService.broadCastMessage(memberId, sendChat);
    }

    private void handleEnterRoom(WebSocketSession session, Long memberId, EnterRoomRequest request) throws Exception {
        Timer.Sample enterRoomSample = startEnterRoomMetric();
        try {
            IdValidator.requireChatRoomId(request.getChatRoomId());
            spaceService.validateParticipant(memberId, request.getChatRoomId());
            WebSocketSession safeSession = websocketSessionManager.getWrappedSession(session);
            spaceManager.addSessionToSpace(safeSession, request.getChatRoomId());
            sendEnterRoomAck(safeSession, request.getChatRoomId());
            stopEnterRoomMetric(enterRoomSample);
        } catch (CustomException e) {
            recordEnterRoomError(mapMetricReason(e.getErrorCode()));
            throw e;
        } catch (Exception e) {
            recordEnterRoomError("INTERNAL_ERROR");
            throw e;
        }
    }

    private void handleRoomActive(WebSocketSession session, Long memberId, RoomActiveRequest request) {
        Long activeRoomId = request.getChatRoomId();
        spaceManager.activateSpace(session.getId(), activeRoomId);
        messageService.onRoomActive(memberId, activeRoomId);
    }

    private void handleRoomInactive(WebSocketSession session, RoomInactiveRequest request) {
        spaceManager.deactivateSpace(session.getId(), request.getChatRoomId());
    }

    private void handleReadUpTo(WebSocketSession session, Long memberId, ReadUpToRequest request) {
        Long readUpToRoomId = request.getChatRoomId();

        if (!isSessionInRoom(session, readUpToRoomId)) {
            sendError(session, request.getMessageType(), readUpToRoomId, null, ErrorCode.ROOM_NOT_JOINED);
            return;
        }

        messageService.onReadUpTo(memberId, readUpToRoomId, request.getLastReadMessageId());
    }

    private void handleDiscussionMessage(Long memberId, SendDiscussionMessage request) {
        discussionMessageService.broadcastDiscussionMessage(
                request.getDiscussionId(),
                memberId,
                request.getContent()
        );
    }

    private boolean isSessionInRoom(WebSocketSession session, Long chatRoomId) {
        return spaceManager.getWebSocketSessionBy(chatRoomId).stream()
                .anyMatch(s -> s.getId().equals(session.getId()));
    }

    private void sendEnterRoomAck(WebSocketSession session, Long chatRoomId) {
        if (!session.isOpen()) return;
        try {
            EnterRoomAckResponse ack = EnterRoomAckResponse.builder()
                    .messageType(MessageType.ENTER_ROOM_ACK)
                    .chatRoomId(chatRoomId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        } catch (IOException e) {
            log.warn("WS 전송 실패: ENTER_ROOM_ACK, session={}, chatRoomId={}", session.getId(), chatRoomId, e);
        }
    }

    private void sendError(WebSocketSession session, MessageType requestType, Long chatRoomId,
                            String clientMessageId, ErrorCode errorCode) {
        if (!session.isOpen()) return;
        try {
            ErrorResponse error = ErrorResponse.builder()
                    .messageType(MessageType.ERROR)
                    .requestType(requestType)
                    .chatRoomId(chatRoomId)
                    .clientMessageId(clientMessageId)
                    .errorCode(mapErrorCode(errorCode))
                    .message(errorCode.getErrorMessage())
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (IOException e) {
            log.warn("WS 전송 실패: ERROR 이벤트, session={}, requestType={}, chatRoomId={}, errorCode={}",
                    session.getId(), requestType, chatRoomId, errorCode != null ? errorCode.name() : null, e);
        }
    }

    private Long extractChatRoomId(BaseWebSocketMessage baseMessage) {
        if (baseMessage instanceof EnterRoomRequest r) return r.getChatRoomId();
        if (baseMessage instanceof SendChat r) return r.getChatRoomId();
        if (baseMessage instanceof RoomActiveRequest r) return r.getChatRoomId();
        if (baseMessage instanceof RoomInactiveRequest r) return r.getChatRoomId();
        if (baseMessage instanceof ReadUpToRequest r) return r.getChatRoomId();
        return null;
    }

    private String extractClientMessageId(BaseWebSocketMessage baseMessage) {
        if (baseMessage instanceof SendChat r) return r.getClientMessageId();
        return null;
    }

    private Timer.Sample startEnterRoomMetric() {
        meterRegistry.counter(WsMetricNames.WS_ENTER_ROOM_TOTAL).increment();
        return Timer.start(meterRegistry);
    }

    private void stopEnterRoomMetric(Timer.Sample sample) {
        sample.stop(meterRegistry.timer(WsMetricNames.WS_ENTER_ROOM_ACK_DURATION));
    }

    private void recordEnterRoomError(String reason) {
        meterRegistry.counter(WsMetricNames.WS_ENTER_ROOM_ERROR_TOTAL, "reason", reason).increment();
    }

    private String mapCommonReason(ErrorCode errorCode) {
        return switch (errorCode) {
            case SPACE_NOT_FOUND -> "ROOM_NOT_FOUND";
            // TODO: MEMBER_NOT_FOUND는 "회원을 찾을 수 없음"과 "인증되지 않음"이 의미상 다르지만,
            // 현재 errorCode 구조에 별도 코드가 없어 최소 변경으로 UNAUTHORIZED에 임시 매핑한다.
            // 클라이언트가 두 케이스를 구분해야 할 필요가 생기면 별도 errorCode(예: MEMBER_NOT_FOUND)로 분리할 것.
            case USER_NOT_AUTHENTICATED, MEMBER_NOT_FOUND -> "UNAUTHORIZED";
            case EMPTY_MESSAGE_CONTENT, INVALID_MESSAGE_FORMAT, UNKNOWN_MESSAGE_TYPE, INVALID_CLIENT_MESSAGE_ID -> "INVALID_MESSAGE";
            case UNEXPECTED_ERROR -> "INTERNAL_ERROR";
            default -> null;
        };
    }

    private String mapErrorCode(ErrorCode errorCode) {
        String common = mapCommonReason(errorCode);
        if (common != null) return common;
        return switch (errorCode) {
            case ROOM_NOT_JOINED -> "ROOM_NOT_JOINED";
            case MESSAGE_NOT_FOUND -> "MESSAGE_NOT_FOUND";
            default -> "INTERNAL_ERROR";
        };
    }

    private String mapMetricReason(ErrorCode errorCode) {
        String common = mapCommonReason(errorCode);
        return common != null ? common : "UNKNOWN";
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

        if (loginMemberId == null) {
            log.warn("afterConnectionClosed: SESSION_ID not found, session={}", session.getId());
            return;
        }

        memberService.removeSession(loginMemberId, session);
    }

}
