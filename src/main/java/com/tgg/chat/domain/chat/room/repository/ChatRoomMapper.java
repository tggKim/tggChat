package com.tgg.chat.domain.chat.room.repository;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
public interface ChatRoomMapper {

    public boolean existsDirectChatRoom(Long userId1, Long userId2);

}
