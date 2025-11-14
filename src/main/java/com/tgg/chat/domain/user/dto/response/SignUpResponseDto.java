package com.tgg.chat.domain.user.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tgg.chat.domain.user.entity.User;

import lombok.Getter;

@Getter
public class SignUpResponseDto {
	
	private final Long userId;
	private final String username;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private final LocalDateTime createdAt;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private final LocalDateTime updatedAt;
	
	private SignUpResponseDto(Long userId, String username, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.userId = userId;
		this.username = username;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
	
	public static SignUpResponseDto of(User user) {
		return new SignUpResponseDto(user.getUserId(), user.getUsername(), user.getCreatedAt(), user.getUpdatedAt());
	}
	
}
