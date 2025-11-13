package com.tgg.chat.domain.user.dto.response;

import java.time.LocalDateTime;

import com.tgg.chat.domain.user.entity.User;

import lombok.Getter;

@Getter
public class SignUpResponseDto {
	
	private final Long id;
	private final String username;
	private final LocalDateTime createdAt;
	private final LocalDateTime updatedAt;
	
	private SignUpResponseDto(Long id, String username, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.username = username;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
	
	public static SignUpResponseDto of(User user) {
		return new SignUpResponseDto(user.getId(), user.getUsername(), user.getCreatedAt(), user.getUpdatedAt());
	}
	
}
