package com.chat.repository;

import com.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT c" +
            " FROM Message c" +
            " WHERE c.space.id = :chatRoomId ORDER BY c.id DESC")
    List<Message> findLastMessageBy(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    @Query("SELECT MAX(c.id)" +
            " FROM Message c" +
            " WHERE c.space.id = :chatRoomId")
    Optional<Long> findLastMessageIdBy(@Param("chatRoomId") Long chatRoomId);

    Optional<Message> findByClientMessageId(String clientMessageId);

    boolean existsByIdAndSpaceId(Long id, Long spaceId);

    @Query("SELECT c" +
            " FROM Message c" +
            " WHERE c.id IN (" +
            "   SELECT MAX(c2.id)" +
            "   FROM Message c2" +
            "   WHERE c2.space.id IN :chatRoomIds" +
            "   GROUP BY c2.space.id" +
            " )")
    List<Message> findLastMessagesBy(@Param("chatRoomIds") List<Long> chatRoomIds);

    @Query("SELECT c" +
            " FROM Message c" +
            " JOIN FETCH c.member" +
            " WHERE c.space.id = :chatRoomId" +
            " ORDER BY c.id DESC")
    List<Message> findLatestMessages(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    @Query("SELECT c" +
            " FROM Message c" +
            " JOIN FETCH c.member" +
            " WHERE c.space.id = :chatRoomId" +
            " AND c.id < :beforeChatId" +
            " ORDER BY c.id DESC")
    List<Message> findMessagesBeforeId(@Param("chatRoomId") Long chatRoomId,
                                    @Param("beforeChatId") Long beforeChatId,
                                    Pageable pageable);
}
