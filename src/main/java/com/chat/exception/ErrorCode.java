package com.chat.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    EMPTY_USERNAME(HttpStatus.BAD_REQUEST, "아이디가 비어있습니다."),
    EMPTY_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 비어있습니다."),
    EMPTY_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임이 비어있습니다."),
    EMPTY_SPACE_TITLE(HttpStatus.BAD_REQUEST, "Space 이름이 비어있습니다."),
    EMPTY_MESSAGE_CONTENT(HttpStatus.BAD_REQUEST, "메시지 내용이 비어있습니다."),
    EMPTY_DISCUSSION_MESSAGE_CONTENT(HttpStatus.BAD_REQUEST, "Discussion 메시지 내용이 비어있습니다."),
    INVALID_CLIENT_MESSAGE_ID(HttpStatus.BAD_REQUEST, "clientMessageId는 필수입니다."),
    CLIENT_MESSAGE_ID_CONFLICT(HttpStatus.CONFLICT, "이미 다른 요청에 사용된 clientMessageId입니다."),

    DUPLICATED_USERNAME(HttpStatus.BAD_REQUEST, "이미 사용 중인 아이디입니다."),
    USERNAME_NOT_MATCH(HttpStatus.BAD_REQUEST, "아이디가 일치하지 않습니다."),
    PASSWORD_NOT_MATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    USER_NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원이 존재하지 않습니다."),
    MEMBERS_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원이 포함돼있습니다."),
    DISCUSSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 Discussion입니다."),
    INCLUDE_SENDER_IN_RECEIVERS(HttpStatus.BAD_REQUEST, "받는 사람 목록에 자신을 포함할 수 없습니다."),

    SPACE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 존재하는 Space입니다."),
    SPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 Space입니다."),
    SPACE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 Space에 접근 권한이 없습니다."),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 메시지입니다."),
    DISCUSSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 Discussion입니다."),

    SPACE_BROADCAST_IO_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "Space 전파 중 오류가 발생했습니다."),
    WEB_SOCKET_SESSION_NOT_EXIST(HttpStatus.INTERNAL_SERVER_ERROR, "웹소켓 세션이 존재하지 않습니다."),

    ROOM_NOT_JOINED(HttpStatus.FORBIDDEN, "참여 중인 채팅방이 아닙니다. ENTER_ROOM 후 다시 시도해주세요."),
    INVALID_MESSAGE_FORMAT(HttpStatus.BAD_REQUEST, "메시지 형식이 올바르지 않습니다."),
    UNKNOWN_MESSAGE_TYPE(HttpStatus.BAD_REQUEST, "알 수 없는 메시지 타입입니다."),
    UNEXPECTED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    SERVER_BUSY(HttpStatus.TOO_MANY_REQUESTS, "서버가 일시적으로 혼잡합니다. 잠시 후 다시 시도해주세요."),
    ;

    private final HttpStatus status;
    private final String errorMessage;
}