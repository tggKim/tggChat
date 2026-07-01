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
    Optional<ChatRoom> findByChatRoomTypeAndDirectUser1AndDirectUser2(ChatRoomType chatRoomType, User directUser1, User directUser2);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              select cr
              from ChatRoom cr
              where cr.chatRoomId = :chatRoomId
      """)
    Optional<ChatRoom> findByIdForUpdate(Long chatRoomId);
}
