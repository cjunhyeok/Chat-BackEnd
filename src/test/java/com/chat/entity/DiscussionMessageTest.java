package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.chat.fixture.ClientMessageIdFixture.nextClientMessageId;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscussionMessageTest {

    @Test
    @DisplayName("내용이 null이면 DiscussionMessage 생성 시 EMPTY_DISCUSSION_MESSAGE_CONTENT 예외가 발생한다.")
    void 내용이_null이면_DiscussionMessage_생성_시_EMPTY_DISCUSSION_MESSAGE_CONTENT_예외가_발생한다() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Discussion discussion = createDiscussion(member);

        // when & then
        assertThatThrownBy(() -> DiscussionMessage.of(null, discussion, member))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_DISCUSSION_MESSAGE_CONTENT);
    }

    @Test
    @DisplayName("내용이 공백이면 DiscussionMessage 생성 시 EMPTY_DISCUSSION_MESSAGE_CONTENT 예외가 발생한다.")
    void 내용이_공백이면_DiscussionMessage_생성_시_EMPTY_DISCUSSION_MESSAGE_CONTENT_예외가_발생한다() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Discussion discussion = createDiscussion(member);

        // when & then
        assertThatThrownBy(() -> DiscussionMessage.of("  ", discussion, member))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_DISCUSSION_MESSAGE_CONTENT);
    }

    @Test
    @DisplayName("Discussion이 null이면 DiscussionMessage 생성 시 DISCUSSION_NOT_FOUND 예외가 발생한다.")
    void Discussion이_null이면_DiscussionMessage_생성_시_DISCUSSION_NOT_FOUND_예외가_발생한다() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when & then
        assertThatThrownBy(() -> DiscussionMessage.of("답글입니다", null, member))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DISCUSSION_NOT_FOUND);
    }

    @Test
    @DisplayName("Member가 null이면 DiscussionMessage 생성 시 MEMBER_NOT_FOUND 예외가 발생한다.")
    void Member가_null이면_DiscussionMessage_생성_시_MEMBER_NOT_FOUND_예외가_발생한다() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Discussion discussion = createDiscussion(member);

        // when & then
        assertThatThrownBy(() -> DiscussionMessage.of("답글입니다", discussion, null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    private Discussion createDiscussion(Member member) {
        Space space = Space.of("개발팀");
        Message rootMessage = Message.of("안녕하세요", member, space, nextClientMessageId());
        return Discussion.of(rootMessage);
    }
}
