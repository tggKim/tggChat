package com.tgg.chat.domain.chat.dto.response;

import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description =  "채팅방 사용자 세부 정보 조회 응답 DTO")
public class FindChatRoomMembersResponseDto {
    @Schema(description = "유저 ID", example = "1")
    private final Long userId;

    @Schema(description = "유저명", example = "tgg")
    private final String username;

    @Schema(description = "유저 프로필 이미지 키", example = "key")
    private final String profileImageKey;

    @Schema(description = "유저의 권한", example = "OWNER")
    private final ChatRoomUserRole chatRoomUserRole;

    @Schema(description = "요청 사용자가 해당 유저를 친구로 추가할 수 있는지 여부", example = "true")
    private final boolean canAddFriend;

    private FindChatRoomMembersResponseDto(Long userId, String username, String profileImageKey, ChatRoomUserRole chatRoomUserRole, boolean canAddFriend) {
        this.userId = userId;
        this.username = username;
        this.profileImageKey = profileImageKey;
        this.chatRoomUserRole = chatRoomUserRole;
        this.canAddFriend = canAddFriend;
    }

    public static FindChatRoomMembersResponseDto of(Long userId, String username, String profileImageKey, ChatRoomUserRole chatRoomUserRole, boolean canAddFriend) {
        return new FindChatRoomMembersResponseDto(userId, username, profileImageKey, chatRoomUserRole, canAddFriend);
    }
}
