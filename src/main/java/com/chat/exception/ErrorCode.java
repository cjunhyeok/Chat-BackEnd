package com.chat.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    EMPTY_USERNAME(HttpStatus.BAD_REQUEST, "ID 가 비어있습니다."),
    DUPLICATED_USERNAME(HttpStatus.BAD_REQUEST, "중복된 ID 입니다."),
    USERNAME_NOT_MATCH(HttpStatus.BAD_REQUEST, "ID 가 일치하지 않습니다."),
    PASSWORD_NOT_MATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    USER_NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "회원이 존재하지 않습니다."),
    MEMBERS_NOT_FOUDN(HttpStatus.BAD_REQUEST, "존재하지 않는 회원이 포함돼있습니다."),
    Include_Sender_In_Receivers(HttpStatus.BAD_REQUEST, "받는사람 목록에 자신이 포함될 수 없습니다."),
    CHAT_ROOM_ALREADY_EXIST(HttpStatus.BAD_REQUEST, "이미 존재하는 채팅방입니다."),
    CHAT_ROOM_NOT_EXIST(HttpStatus.BAD_REQUEST, "존재하지 않는 채팅방입니다."),
    CHAT_NOT_EXIST(HttpStatus.BAD_REQUEST, "존재하지 않는 채팅입니다."),
    CHAT_ROOM_BROADCAST_IO_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "채팅방 전파 중 오류가 발생했습니다."),
    WEB_SOCKET_SESSION_NOT_EXIST(HttpStatus.INTERNAL_SERVER_ERROR, "웹소켓 세션이 존재하지 않습니다."),
    CHAT_ROOM_SESSION_NOT_EXIST(HttpStatus.INTERNAL_SERVER_ERROR, "채팅방 세션이 존재하지 않습니다."),
    ;

    private final HttpStatus status;
    private final String errorMessage;
}
