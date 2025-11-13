package com.tgg.chat.domain.user.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tgg.chat.domain.user.entity.User;

import lombok.Getter;

@Getter
public class UserResponseDto {
	
	private final Long id;
	private final String email;
	private final String username;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private final LocalDateTime createdAt;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private final LocalDateTime updatedAt;
	
	private UserResponseDto(Long id, String email, String username, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.email = email;
		this.username = username;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
	
	public static UserResponseDto of(User user) {
		return new UserResponseDto(user.getId(), user.getEmail(), user.getUsername(), user.getCreatedAt(), user.getUpdatedAt());
	}

}
