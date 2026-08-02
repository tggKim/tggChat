package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.dto.query.ChatRoomListBaseRowDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatRoomMapper {
    List<ChatRoomListBaseRowDto> findActiveChatRoomsByUserId(Long userId);
}
