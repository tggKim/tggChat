package com.tgg.chat.domain.chat.room.repository;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatRoomUserMapper {

    public boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);

}
