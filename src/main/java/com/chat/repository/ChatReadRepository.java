package com.chat.repository;

import com.chat.entity.ChatRead;
import com.chat.service.dtos.LastChatRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatReadRepository extends JpaRepository<ChatRead, Long> {

    @Query("SELECT COUNT (cre)" +
            " FROM ChatRead cre" +
            " JOIN Chat c ON cre.chat.id = c.id" +
            " JOIN ChatRoom cro ON c.chatRoom.id = cro.id" +
            " WHERE cro.id = :chatRoomId" +
            " AND cre.member.id = :memberId" +
            " AND cre.isRead = false")
    Long findUnReadCountBy(@Param("chatRoomId") Long chatRoomId,
                           @Param("memberId") Long memberId);

    @Query("SELECT COUNT (cre)" +
            " FROM ChatRead cre" +
            " JOIN Chat c ON cre.chat.id = c.id" +
            " WHERE c.id = :chatId" +
            " AND cre.isRead = false")
    Long findUnReadCountBy(@Param("chatId") Long chatId);

    @Query("SELECT cr" +
            " FROM ChatRead cr" +
            " WHERE cr.chat.id = :chatId" +
            " and cr.member.id = :memberId")
    ChatRead findBy(@Param("chatId") Long chatId, @Param("memberId") Long memberId);

    @Query("SELECT new com.chat.service.dtos.LastChatRead(cr.member.id, COALESCE(MAX(CASE WHEN cr.isRead = true THEN c.id ELSE 0 END), 0))" +
            "FROM ChatRead cr " +
            "JOIN cr.chat c " +
            "JOIN c.chatRoom r " +
            "WHERE r.id = :chatRoomId " +
            "GROUP BY cr.member.id")
    List<LastChatRead> findLastReadChatsBy(@Param("chatRoomId") Long chatRoomId);

    @Query("SELECT new com.chat.service.dtos.LastChatRead(cr.member.id, MAX(c.id))" +
            " FROM ChatRead cr" +
            " JOIN Chat c ON c.id = cr.chat.id" +
            " WHERE cr.member.id = :memberId" +
            " AND c.chatRoom.id = :chatRoomId" +
            " AND cr.isRead = true" +
            " GROUP BY cr.member.id" +
            " ORDER BY MAX(c.id) DESC")
    List<LastChatRead> findLastReadChatBy(@Param("memberId") Long memberId, @Param("chatRoomId") Long chatRoomId);

    @Modifying
    @Query("UPDATE ChatRead cr " +
            "SET cr.isRead = true " +
            "WHERE cr.chat.chatRoom.id = :chatRoomId " +
            "AND cr.member.id = :memberId " +
            "AND cr.isRead = false")
    int updateUnreadChatReadsToRead(@Param("memberId") Long memberId,
                                    @Param("chatRoomId") Long chatRoomId);
}
