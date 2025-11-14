package com.tgg.chat.domain.user.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tgg.chat.domain.user.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "유저 조회 응답 DTO")
public class UserResponseDto {
	
	@Schema(description = "생성된 유저 식별 ID", example = "1")
	private final Long userId;
	
	@Schema(description = "이메일", example = "test@example.com")
	private final String email;
	
	@Schema(description = "유저 이름", example = "user1")
	private final String username;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Schema(description = "유저 조회 응답 DTO", example = "2025-02-13 14:23:44", type="string")
	private final LocalDateTime createdAt;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Schema(description = "유저 조회 응답 DTO", example = "2025-02-13 14:23:44", type="string")
	private final LocalDateTime updatedAt;
	
	private UserResponseDto(Long userId, String email, String username, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.userId = userId;
		this.email = email;
		this.username = username;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
	
	public static UserResponseDto of(User user) {
		return new UserResponseDto(user.getUserId(), user.getEmail(), user.getUsername(), user.getCreatedAt(), user.getUpdatedAt());
	}

}
