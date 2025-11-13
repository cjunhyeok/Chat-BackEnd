package com.chat.service;

import com.chat.api.response.chatroom.ChatRoomsResponse;
import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.ChatRoomParticipantRepository;
import com.chat.repository.ChatRoomRepository;
import com.chat.service.dtos.SaveChatRoomDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatRoomServiceTest {

    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private TestDataFixture fixture;

    @Test
    @DisplayName("채팅방을 저장한다.")
    void saveChatRoomTest() {
        // given
        String title = "title";

        Member sender = fixture.savedMemberBy("sender");
        Member firstReceiver = fixture.savedMemberBy("firstReceiver");
        Member secondReceiver = fixture.savedMemberBy("secondReceiver");

        Set<Long> receiverIds = new HashSet<>();
        receiverIds.add(firstReceiver.getId());
        receiverIds.add(secondReceiver.getId());

        SaveChatRoomDTO dto = SaveChatRoomDTO
                .builder()
                .title(title)
                .senderId(sender.getId())
                .receiverIds(receiverIds)
                .build();

        // when
        Long savedChatRoomId = chatRoomService.saveChatRoom(dto);

        // then
        ChatRoom chatRoom = chatRoomRepository.findById(savedChatRoomId).get();

        List<ChatRoomParticipant> chatRoomParticipants
                = chatRoomParticipantRepository.findAllFetchMemberBy(savedChatRoomId);

        Set<Long> participantMemberIds = chatRoomParticipants.stream()
                .map(p -> p.getMember().getId())
                .collect(Collectors.toSet());

        Set<Long> expectedMemberIds = Set.of(
                sender.getId(),
                firstReceiver.getId(),
                secondReceiver.getId()
        );

        assertThat(chatRoom.getTitle()).isEqualTo(title);
        assertThat(participantMemberIds)
                .hasSize(3)
                .containsExactlyInAnyOrderElementsOf(expectedMemberIds);
    }

    @Test
    @DisplayName("채팅방 목록을 조회한다.")
    void findChatRoomsTest() {
        // given
        Member first = fixture.savedMemberBy("first");
        Member second = fixture.savedMemberBy("second");
        Member third = fixture.savedMemberBy("third");
        Member fourth = fixture.savedMemberBy("fourth");

        Long firstId = first.getId();
        List<Member> secondParticipants = createParticipantsBy(first, second);
        fixture.savedChatRoomBy("title", secondParticipants);

        List<Member> thirdParticipants = createParticipantsBy(first, third);
        fixture.savedChatRoomBy("title", thirdParticipants);

        List<Member> fourthParticipants = createParticipantsBy(first, fourth);
        fixture.savedChatRoomBy("title", fourthParticipants);

        // when
        List<ChatRoomsResponse> chatRooms = chatRoomService.findChatRooms(first.getId());

        // then
        assertThat(chatRooms).hasSize(3);
    }

    // todo 채팅, 안읽은 채팅 수 테스트 필요

    // todo connect & broadCastMessage 테스트 필요

    private List<Member> createParticipantsBy(Member first, Member second) {
        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        return participants;
    }
}