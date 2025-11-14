package com.tgg.chat.domain.user.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tgg.chat.domain.user.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "회원 가입 응답 DTO")
public class SignUpResponseDto {
	
	@Schema(description = "생성된 유저 식별 ID", example = "1")
	private final Long userId;
	
	@Schema(description = "유저 이름", example = "user1")
	private final String username;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Schema(description = "생성 시간", example = "2025-02-13 14:23:44", type = "string")
	private final LocalDateTime createdAt;
	
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Schema(description = "수정 시간", example = "2025-02-13 14:23:44", type = "string")
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
