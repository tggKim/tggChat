package com.tgg.chat.domain.chat.repository;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.tgg.chat.domain.chat.dto.query.ChatMessageListRowDto;
import com.tgg.chat.domain.chat.entity.ChatMessage;

@Mapper
public interface ChatMessageMapper {
	
	public List<ChatMessageListRowDto> findChatMessages(Long userId, Long chatRoomId, Long offsetSeq);
	
}
