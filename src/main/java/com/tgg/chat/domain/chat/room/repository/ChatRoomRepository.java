package com.tgg.chat.domain.chat.room.repository;

import com.tgg.chat.domain.chat.room.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

}
