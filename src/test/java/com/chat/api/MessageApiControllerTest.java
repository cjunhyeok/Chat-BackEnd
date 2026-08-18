package com.chat.api;

import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.TestDataFixture;
import com.chat.service.MessageService;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.chat.fixture.ClientMessageIdFixture.nextClientMessageId;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MessageApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MessageService messageService;

    @Test
    @DisplayName("초기 진입 조회는 200과 메시지 목록을 반환한다.")
    void chatHistory_initialLoad_returns200WithMessagesTest() throws Exception {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        messageService.saveMessage(member.getId(), chatRoom.getId(), "first", nextClientMessageId());
        messageService.saveMessage(member.getId(), chatRoom.getId(), "second", nextClientMessageId());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, member.getId());

        // when & then
        mockMvc.perform(get("/api/chats")
                        .param("chatRoomId", String.valueOf(chatRoom.getId()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    @DisplayName("beforeChatId 없이 요청해도 400이 아닌 200을 반환한다.")
    void chatHistory_beforeChatIdIsOptional_returns200Test() throws Exception {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, member.getId());

        // when & then: beforeChatId 파라미터 없이 요청 → required=false 이므로 200
        mockMvc.perform(get("/api/chats")
                        .param("chatRoomId", String.valueOf(chatRoom.getId()))
                        .session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("커서 기반 조회는 beforeChatId 이전 메시지를 반환하고 lastReadChatId는 null이다.")
    void chatHistory_cursorLoad_returnsPreviousMessagesAndNullLastReadChatIdTest() throws Exception {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        messageService.saveMessage(member.getId(), chatRoom.getId(), "first", nextClientMessageId());
        messageService.saveMessage(member.getId(), chatRoom.getId(), "second", nextClientMessageId());
        Long thirdChatId = messageService.saveMessage(member.getId(), chatRoom.getId(), "third", nextClientMessageId()).getId();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, member.getId());

        // when & then: thirdChatId 커서 → first, second 반환 / lastReadChatId=null
        mockMvc.perform(get("/api/chats")
                        .param("chatRoomId", String.valueOf(chatRoom.getId()))
                        .param("beforeChatId", String.valueOf(thirdChatId))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.lastReadMessageId", nullValue()));
    }

    @Test
    @DisplayName("메시지가 없는 채팅방 조회 시 빈 목록과 hasMore=false를 반환한다.")
    void chatHistory_emptyRoom_returnsEmptyMessagesTest() throws Exception {
        // given
        Member member = fixture.savedMemberBy("member");
        Space chatRoom = fixture.savedChatRoomBy("room", List.of(member));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, member.getId());

        // when & then
        mockMvc.perform(get("/api/chats")
                        .param("chatRoomId", String.valueOf(chatRoom.getId()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isEmpty())
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }
}
