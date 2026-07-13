package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "space")
public class Space extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "space_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "invite_code", nullable = false, unique = true)
    private String inviteCode;

    private Space(String title) {
        validateTitle(title);
        this.title = title;
        this.inviteCode = generateInviteCode();
    }

    public static Space of(String title) {
        return new Space(title);
    }

    public boolean rename(String title) {
        validateTitle(title);
        if (this.title.equals(title)) {
            return false;
        }
        this.title = title;
        return true;
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new CustomException(ErrorCode.EMPTY_SPACE_TITLE);
        }
    }

    private static String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
