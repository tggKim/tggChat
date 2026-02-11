package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.dto.query.ChatRoomListRowDto;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatRoomMapper {

    public List<ChatRoomListRowDto> findAllChatRoomsByUserId(Long userId);
    
    public Long getLastSeqLock(Long chatRoomId);
    
    public int updateLastSeq(Long seq, String lastMessagePreview, LocalDateTime lastMessageAt, Long chatRoomId);

}
