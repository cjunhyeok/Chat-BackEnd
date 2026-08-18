package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "message",
        indexes = {
                @Index(
                        name = "idx_space_id_message_id",
                        columnList = "space_id, message_id DESC"
                )
        }
)
public class Message extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "message_id")
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @Column(name = "client_message_id", nullable = false, updatable = false)
    private String clientMessageId;

    private Message(String content, Member member, Space space, String clientMessageId) {
        validateContent(content);
        validateMember(member);
        validateSpace(space);
        validateClientMessageId(clientMessageId);

        this.content = content;
        this.member = member;
        this.space = space;
        this.clientMessageId = clientMessageId;
    }

    public static Message of(String content, Member member, Space space, String clientMessageId) {
        return new Message(content, member, space, clientMessageId);
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new CustomException(ErrorCode.EMPTY_MESSAGE_CONTENT);
        }
    }

    private static void validateMember(Member member) {
        if (member == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private static void validateSpace(Space space) {
        if (space == null) {
            throw new CustomException(ErrorCode.SPACE_NOT_FOUND);
        }
    }

    private static void validateClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_CLIENT_MESSAGE_ID);
        }
    }
}
