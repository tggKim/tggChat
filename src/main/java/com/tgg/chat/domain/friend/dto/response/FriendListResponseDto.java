package com.tgg.chat.domain.friend.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "친구 목록 응답 DTO")
public class FriendListResponseDto {

	@Schema(description = "추가하고자 하는 유저 ID", example = "1")
	private final Long friendId;
	
	@Schema(description = "유저 이름", example = "user1")
	private final String username;
	
	private FriendListResponseDto(Long friendId, String username) {
		this.friendId = friendId;
		this.username = username;
	}
	
	public static FriendListResponseDto of(Long friendId, String username) {
		return new FriendListResponseDto(friendId, username);
	}
	
}
