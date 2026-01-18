package com.tgg.chat.domain.chat.room.repository;

import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomMapper {

    public boolean existsDirectChatRoom(Long userId1, Long userId2);

}
