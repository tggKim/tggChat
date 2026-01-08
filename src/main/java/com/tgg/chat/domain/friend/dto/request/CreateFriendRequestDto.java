package com.tgg.chat.domain.friend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "친구 생성 요청 DTO")
public class CreateFriendRequestDto {

	@Schema(description = "추가하고자 하는 유저명", example = "tgg")
    private String username;

}
