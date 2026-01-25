package com.tgg.chat.domain.chat.room.repository;

import com.tgg.chat.domain.chat.room.entity.ChatRoom;
import com.tgg.chat.domain.chat.room.enums.ChatRoomType;
import com.tgg.chat.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByChatRoomTypeAndDirectUser1AndDirectUser2(ChatRoomType chatRoomType, User directUser1, User directUser2);

}
