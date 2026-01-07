package com.tgg.chat.domain.friend.dto.query;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserFriendRowDto {

    private Long userFriendId;

    private Long ownerId;

    private Long friendId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
