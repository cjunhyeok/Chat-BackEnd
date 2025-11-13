package com.chat.repository;

import com.chat.entity.ChatRoom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatRoomRepositoryTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Test
    @DisplayName("채팅방 제목을 지정한 채팅방을 저장한다.")
    void saveTestByTitle() {
        // given
        String title = "title";
        ChatRoom chatRoom = ChatRoom.of(title);

        // when
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        // then
        assertThat(savedChatRoom.getId()).isNotNull();
        assertThat(savedChatRoom.getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("참여자 정보를 이용해 채팅방을 저장한다.")
    void saveTestByParticipants() {
        // given
        List<String> participants = new ArrayList<>();
        participants.add("one");
        participants.add("two");
        participants.add("three");
        ChatRoom chatRoom = ChatRoom.of(generateDefaultTitle(participants));

        // when
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        // then
        assertThat(savedChatRoom.getId()).isNotNull();
        assertThat(savedChatRoom.getTitle())
                .isEqualTo(participants.stream()
                        .sorted().collect(Collectors.joining(", ")));
    }

    private String generateDefaultTitle(List<String> participants) {
        return participants.stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }
}