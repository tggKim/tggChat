package com.tgg.chat.domain.chat.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tgg.chat.domain.chat.room.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	
}
