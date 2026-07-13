package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceTest {

    @Test
    @DisplayName("제목이 null이면 Space 생성 시 EMPTY_SPACE_TITLE 예외가 발생한다.")
    void 제목이_null이면_Space_생성_시_EMPTY_SPACE_TITLE_예외가_발생한다() {
        assertThatThrownBy(() -> Space.of(null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_SPACE_TITLE);
    }

    @Test
    @DisplayName("제목이 공백이면 Space 생성 시 EMPTY_SPACE_TITLE 예외가 발생한다.")
    void 제목이_공백이면_Space_생성_시_EMPTY_SPACE_TITLE_예외가_발생한다() {
        assertThatThrownBy(() -> Space.of("  "))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_SPACE_TITLE);
    }

    @Test
    @DisplayName("유효한 제목으로 변경하면 Space 이름이 갱신되고 true를 반환한다.")
    void 유효한_제목으로_변경하면_Space_이름이_갱신된다() {
        // given
        Space space = Space.of("구 이름");

        // when
        boolean changed = space.rename("신 이름");

        // then
        assertThat(changed).isTrue();
        assertThat(space.getTitle()).isEqualTo("신 이름");
    }

    @Test
    @DisplayName("기존과 같은 제목으로 변경을 시도하면 false를 반환하고 상태가 바뀌지 않는다.")
    void 같은_제목으로_변경하면_false를_반환하고_상태가_바뀌지_않는다() {
        // given
        Space space = Space.of("동일 이름");

        // when
        boolean changed = space.rename("동일 이름");

        // then
        assertThat(changed).isFalse();
        assertThat(space.getTitle()).isEqualTo("동일 이름");
    }

    @Test
    @DisplayName("제목이 null이면 Space 이름을 변경할 수 없다.")
    void 제목이_null이면_Space_이름을_변경할_수_없다() {
        // given
        Space space = Space.of("개발팀");

        // when & then
        assertThatThrownBy(() -> space.rename(null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_SPACE_TITLE);
    }

    @Test
    @DisplayName("제목이 공백이면 Space 이름을 변경할 수 없다.")
    void 제목이_공백이면_Space_이름을_변경할_수_없다() {
        // given
        Space space = Space.of("개발팀");

        // when & then
        assertThatThrownBy(() -> space.rename("  "))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_SPACE_TITLE);
    }

    @Test
    @DisplayName("Space 생성 시 inviteCode가 자동으로 생성된다.")
    void Space_생성_시_inviteCode가_자동으로_생성된다() {
        // when
        Space space = Space.of("개발팀");

        // then
        assertThat(space.getInviteCode()).isNotNull();
        assertThat(space.getInviteCode()).isNotBlank();
        assertThat(space.getInviteCode()).hasSize(32);
    }

    @Test
    @DisplayName("두 Space의 inviteCode는 서로 다르다.")
    void 두_Space의_inviteCode는_서로_다르다() {
        // when
        Space space1 = Space.of("팀A");
        Space space2 = Space.of("팀B");

        // then
        assertThat(space1.getInviteCode()).isNotEqualTo(space2.getInviteCode());
    }

    @Test
    @DisplayName("Space 이름을 변경해도 inviteCode는 바뀌지 않는다.")
    void rename_후_inviteCode는_변하지_않는다() {
        // given
        Space space = Space.of("개발팀");
        String originalInviteCode = space.getInviteCode();

        // when
        space.rename("백엔드팀");

        // then
        assertThat(space.getInviteCode()).isEqualTo(originalInviteCode);
    }
}
