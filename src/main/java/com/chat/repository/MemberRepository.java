package com.chat.repository;

import com.chat.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);
    Optional<Member> findByUsername(String username);

    @Query("SELECT m.id " +
            "FROM Member m " +
            "JOIN ChatRoomParticipant crp ON m.id = crp.member.id " +
            "WHERE crp.chatRoom.id = :chatRoomId")
    List<Long> findMemberIdsIn(@Param("chatRoomId") Long chatRoomId);
}
