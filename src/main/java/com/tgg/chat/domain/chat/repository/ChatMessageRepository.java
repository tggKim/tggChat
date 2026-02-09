package com.tgg.chat.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tgg.chat.domain.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	
}
