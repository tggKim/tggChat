package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    @Query("""
      select cr
      from ChatRoom cr
      where cr.chatRoomType = com.tgg.chat.domain.chat.enums.ChatRoomType.DIRECT
        and cr.directUser1.userId = :directUser1Id
        and cr.directUser2.userId = :directUser2Id
    """)
    Optional<ChatRoom> findDirectChatRoom(Long directUser1Id, Long directUser2Id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              select cr
              from ChatRoom cr
              where cr.chatRoomId = :chatRoomId
      """)
    Optional<ChatRoom> findByIdForUpdate(Long chatRoomId);
}
