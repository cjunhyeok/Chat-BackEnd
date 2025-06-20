package com.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRead extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "chat_read_id")
    private Long id;

    private Boolean isRead;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "chat_id")
    private Chat chat;

    public ChatRead(Boolean isRead, Member member, Chat chat) {
        this.isRead = isRead;
        this.member = member;
        this.chat = chat;
    }

    public void updateIsReadTrue() {
        this.isRead = true;
    }
}
