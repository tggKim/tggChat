package com.tgg.chat.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description =  "채팅방별 초대 가능 유저 목록 조회 응답 DTO")
public class FindInvitableFriendsResponseDto {
    @Schema(description = "유저 ID", example = "1")
    private final Long userId;

    @Schema(description = "유저명", example = "tgg")
    private final String username;

    @Schema(description = "유저 프로필 이미지 키", example = "key")
    private final String profileImageKey;

    private FindInvitableFriendsResponseDto(Long userId, String username, String profileImageKey) {
        this.userId = userId;
        this.username = username;
        this.profileImageKey = profileImageKey;
    }

    public static FindInvitableFriendsResponseDto of(Long userId, String username, String profileImageKey) {
        return new FindInvitableFriendsResponseDto(userId, username, profileImageKey);
    }
}
