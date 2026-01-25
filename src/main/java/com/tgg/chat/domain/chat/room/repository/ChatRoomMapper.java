package com.tgg.chat.domain.chat.room.repository;

import com.tgg.chat.domain.chat.room.dto.query.ChatRoomListRowDto;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
public interface ChatRoomMapper {

    public List<ChatRoomListRowDto> findAllChatRoomsByUserId(Long userId);

}
