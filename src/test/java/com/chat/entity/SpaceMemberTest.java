package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceMemberTest {

    @Test
    @DisplayName("SpaceMember 생성 시 lastReadMessageId 초기값은 null이다.")
    void SpaceMember_생성_시_lastReadMessageId_초기값은_null이다() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Space space = Space.of("개발팀");

        // when
        SpaceMember spaceMember = SpaceMember.of(member, space);

        // then
        assertThat(spaceMember.getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("Member가 null이면 SpaceMember 생성 시 MEMBER_NOT_FOUND 예외가 발생한다.")
    void Member가_null이면_SpaceMember_생성_시_MEMBER_NOT_FOUND_예외가_발생한다() {
        // given
        Space space = Space.of("개발팀");

        // when & then
        assertThatThrownBy(() -> SpaceMember.of(null, space))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("Space가 null이면 SpaceMember 생성 시 SPACE_NOT_FOUND 예외가 발생한다.")
    void Space가_null이면_SpaceMember_생성_시_SPACE_NOT_FOUND_예외가_발생한다() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when & then
        assertThatThrownBy(() -> SpaceMember.of(member, null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
    }
}
