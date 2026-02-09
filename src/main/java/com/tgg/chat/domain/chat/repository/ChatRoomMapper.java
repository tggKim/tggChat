package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.dto.query.ChatRoomListRowDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatRoomMapper {

    public List<ChatRoomListRowDto> findAllChatRoomsByUserId(Long userId);

}
