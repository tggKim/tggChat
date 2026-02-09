package com.tgg.chat.domain.chat.repository;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper {
	
	public Long getLastSeq(Long chatRoomId);

}
