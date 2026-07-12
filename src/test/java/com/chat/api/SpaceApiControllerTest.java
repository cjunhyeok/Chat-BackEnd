package com.chat.api;

import com.chat.entity.Member;
import com.chat.entity.Space;
import com.chat.fixture.TestDataFixture;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpaceApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestDataFixture fixture;

    @Test
    @DisplayName("rename API는 200과 함께 chatRoomId, 서버 확정 title을 응답한다.")
    void renameChatRoom_returns200WithChatRoomIdAndTitleTest() throws Exception {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("oldTitle", List.of(member));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, member.getId());

        // when & then
        mockMvc.perform(patch("/api/chat/room/{chatRoomId}", space.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"newTitle\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chatRoomId").value(space.getId()))
                .andExpect(jsonPath("$.data.title").value("newTitle"));
    }

    @Test
    @DisplayName("같은 title로 rename을 요청해도 200과 함께 현재 title을 응답한다.")
    void renameChatRoom_sameTitle_returns200WithUnchangedTitleTest() throws Exception {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("sameTitle", List.of(member));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, member.getId());

        // when & then
        mockMvc.perform(patch("/api/chat/room/{chatRoomId}", space.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"sameTitle\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chatRoomId").value(space.getId()))
                .andExpect(jsonPath("$.data.title").value("sameTitle"));
    }
}
