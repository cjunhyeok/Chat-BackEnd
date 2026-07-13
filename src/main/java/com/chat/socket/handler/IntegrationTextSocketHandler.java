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
                    SendChat sendChat = (SendChat) baseMessage;
                    Long chatRoomId = sendChat.getChatRoomId();

                    if (spaceManager.getWebSocketSessionBy(chatRoomId).stream()
                            .noneMatch(s -> s.getId().equals(session.getId()))) {
                        log.warn("WS 처리 실패: 미참여 방에 메시지 전송, session={}, memberId={}, chatRoomId={}, messageType={}",
                                session.getId(), memberId, chatRoomId, baseMessage.getMessageType());
                        sendError(session, baseMessage.getMessageType(), chatRoomId,
                                sendChat.getClientMessageId(), ErrorCode.ROOM_NOT_JOINED);
                        break;
                    }

                    log.debug("CHAT_MESSAGE 수신: memberId={}, chatRoomId={}", memberId, chatRoomId);

                    spaceService.broadCastMessage(memberId, sendChat);

                    break;
                case ENTER_ROOM:
                    meterRegistry.counter(WsMetricNames.WS_ENTER_ROOM_TOTAL).increment();
                    Timer.Sample enterRoomSample = Timer.start(meterRegistry);
                    EnterRoomRequest enterRoomRequest = (EnterRoomRequest) baseMessage;
                    try {
                        IdValidator.requireChatRoomId(enterRoomRequest.getChatRoomId());
                        spaceService.validateParticipant(memberId, enterRoomRequest.getChatRoomId());
                        WebSocketSession safeSession = websocketSessionManager.getWrappedSession(session);
                        spaceManager.addSessionToSpace(safeSession, enterRoomRequest.getChatRoomId());
                        sendEnterRoomAck(safeSession, enterRoomRequest.getChatRoomId());
                        enterRoomSample.stop(meterRegistry.timer(WsMetricNames.WS_ENTER_ROOM_ACK_DURATION));
                    } catch (CustomException e) {
                        meterRegistry.counter(WsMetricNames.WS_ENTER_ROOM_ERROR_TOTAL,
                                "reason", mapMetricReason(e.getErrorCode())).increment();
                        throw e;
                    } catch (Exception e) {
                        meterRegistry.counter(WsMetricNames.WS_ENTER_ROOM_ERROR_TOTAL,
                                "reason", "INTERNAL_ERROR").increment();
                        throw e;
                    }
                    break;
                case ROOM_ACTIVE:
                    RoomActiveRequest activeRequest = (RoomActiveRequest) baseMessage;
                    Long activeRoomId = activeRequest.getChatRoomId();
                    spaceManager.activateSpace(session.getId(), activeRoomId);
                    messageService.onRoomActive(memberId, activeRoomId);
                    break;
                case ROOM_INACTIVE:
                    RoomInactiveRequest inactiveRequest = (RoomInactiveRequest) baseMessage;
                    spaceManager.deactivateSpace(session.getId(), inactiveRequest.getChatRoomId());
                    break;
                case READ_UP_TO:
                    ReadUpToRequest readUpToRequest = (ReadUpToRequest) baseMessage;
                    Long readUpToRoomId = readUpToRequest.getChatRoomId();

                    if (spaceManager.getWebSocketSessionBy(readUpToRoomId).stream()
                            .noneMatch(s -> s.getId().equals(session.getId()))) {
                        sendError(session, baseMessage.getMessageType(), readUpToRoomId, null, ErrorCode.ROOM_NOT_JOINED);
                        break;
                    }

                    messageService.onReadUpTo(memberId, readUpToRoomId, readUpToRequest.getLastReadMessageId());
                    break;
                case DISCUSSION_MESSAGE:
                    SendDiscussionMessage sendDiscussionMessage = (SendDiscussionMessage) baseMessage;
                    discussionMessageService.broadcastDiscussionMessage(
                            sendDiscussionMessage.getDiscussionId(),
                            memberId,
                            sendDiscussionMessage.getContent()
                    );
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

    private String mapErrorCode(ErrorCode errorCode) {
        return switch (errorCode) {
            case SPACE_NOT_FOUND -> "ROOM_NOT_FOUND";
            // TODO: MEMBER_NOT_FOUND는 "회원을 찾을 수 없음"과 "인증되지 않음"이 의미상 다르지만,
            // 현재 errorCode 구조에 별도 코드가 없어 최소 변경으로 UNAUTHORIZED에 임시 매핑한다.
            // 클라이언트가 두 케이스를 구분해야 할 필요가 생기면 별도 errorCode(예: MEMBER_NOT_FOUND)로 분리할 것.
            case USER_NOT_AUTHENTICATED, MEMBER_NOT_FOUND -> "UNAUTHORIZED";
            case EMPTY_MESSAGE_CONTENT, INVALID_MESSAGE_FORMAT, UNKNOWN_MESSAGE_TYPE -> "INVALID_MESSAGE";
            case ROOM_NOT_JOINED -> "ROOM_NOT_JOINED";
            case MESSAGE_NOT_FOUND -> "MESSAGE_NOT_FOUND";
            case UNEXPECTED_ERROR -> "INTERNAL_ERROR";
            default -> "INTERNAL_ERROR";
        };
    }

    private String mapMetricReason(ErrorCode errorCode) {
        return switch (errorCode) {
            case SPACE_NOT_FOUND -> "ROOM_NOT_FOUND";
            case USER_NOT_AUTHENTICATED, MEMBER_NOT_FOUND -> "UNAUTHORIZED";
            case EMPTY_MESSAGE_CONTENT, INVALID_MESSAGE_FORMAT, UNKNOWN_MESSAGE_TYPE -> "INVALID_MESSAGE";
            case UNEXPECTED_ERROR -> "INTERNAL_ERROR";
            default -> "UNKNOWN";
        };
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
