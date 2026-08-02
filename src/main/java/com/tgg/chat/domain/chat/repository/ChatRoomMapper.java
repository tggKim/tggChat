package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.dto.query.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatRoomMapper {
    List<ChatRoomListBaseRowDto> findActiveChatRoomsByUserId(Long userId);

    List<ChatRoomMemberCountRowDto> findMemberCountsByChatRoomIds(List<Long> roomIds);

    List<ChatRoomPreviewUserRowDto> findPreviewUsersByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);

    List<ChatRoomLatestMessageRowDto> findLatestVisibleMessagesByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);

    List<ChatRoomUnreadCountRowDto> findUnreadMessageCountsByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);
}
