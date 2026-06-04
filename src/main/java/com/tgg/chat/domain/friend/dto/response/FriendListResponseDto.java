package com.tgg.chat.domain.friend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "친구 목록 응답 DTO")
public class FriendListResponseDto {

	@Schema(description = "추가하고자 하는 유저 ID", example = "1")
	private final Long friendId;
	
	@Schema(description = "유저 이름", example = "user1")
	private final String friendUsername;
	
	private FriendListResponseDto(Long friendId, String friendUsername) {
		this.friendId = friendId;
		this.friendUsername = friendUsername;
	}
	
	public static FriendListResponseDto of(Long friendId, String friendUsername) {
		return new FriendListResponseDto(friendId, friendUsername);
	}
	
}
