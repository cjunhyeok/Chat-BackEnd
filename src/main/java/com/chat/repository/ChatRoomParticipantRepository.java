package com.chat.repository;

import com.chat.entity.ChatRoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {

    @Query("SELECT crp.chatRoom.id" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.member.id IN :memberIds" +
            " GROUP BY crp.chatRoom.id" +
            " HAVING COUNT(DISTINCT crp.member.id) = :size" +
            " AND COUNT(DISTINCT crp.member.id) =" +
            " (SELECT COUNT(DISTINCT sub.member.id)" +
            " FROM ChatRoomParticipant sub" +
            " WHERE sub.chatRoom.id = crp.chatRoom.id)")
    List<Long> findChatRoomIdsByExactMembers(@Param("memberIds") List<Long> memberIds,
                                             @Param("size") long size);

    @Query(value = "SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.member.id = :memberId")
    List<ChatRoomParticipant> findAllBy(@Param("memberId") Long memberId);

    @Query(value = "SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " JOIN FETCH crp.member" +
            " WHERE crp.chatRoom.id = :chatRoomId")
    List<ChatRoomParticipant> findAllFetchMemberBy(@Param("chatRoomId") Long chatRoomId);

    @Query("SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.chatRoom.id = :chatRoomId" +
            " AND crp.member.id = :memberId")
    ChatRoomParticipant findChatRoomBy(@Param("chatRoomId") Long chatRoomId,
                                       @Param("memberId") Long memberId);
}
