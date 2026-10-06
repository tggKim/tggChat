package com.tgg.chat.domain.friend.dto.response;

import com.tgg.chat.domain.user.entity.User;
import lombok.Getter;

@Getter
public class SearchFriendResponseDto {

    private final Long userId;
    private final String username;
    private final String email;
    private final String profileImageKey;

    private SearchFriendResponseDto(
            Long userId,
            String username,
            String email,
            String profileImageKey
    ) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.profileImageKey = profileImageKey;
    }

    public static SearchFriendResponseDto of(User user) {
        return new SearchFriendResponseDto(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getProfileImageKey()
        );
    }
}
