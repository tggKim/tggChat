package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.dto.query.ChatRoomListBaseRowDto;
import com.tgg.chat.domain.chat.dto.query.ChatRoomMemberCountRowDto;
import com.tgg.chat.domain.chat.dto.query.ChatRoomPreviewUserRowDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatRoomMapper {
    List<ChatRoomListBaseRowDto> findActiveChatRoomsByUserId(Long userId);

    List<ChatRoomMemberCountRowDto> findMemberCountsByChatRoomIds(List<Long> roomIds);

    List<ChatRoomPreviewUserRowDto> findPreviewUsersByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);
}
