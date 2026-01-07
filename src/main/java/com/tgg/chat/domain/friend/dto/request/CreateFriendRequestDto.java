package com.tgg.chat.domain.friend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "친구 생성 요청 DTO")
public class CreateFriendRequestDto {

    private Long friendId;

}
