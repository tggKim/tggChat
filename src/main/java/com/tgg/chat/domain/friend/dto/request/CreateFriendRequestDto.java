package com.tgg.chat.domain.friend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "친구 생성 요청 DTO")
public class CreateFriendRequestDto {
    @NotBlank(message = "사용자명은 필수입니다.")
    @Size(max = 50, message = "사용자명 길이는 50자 이하입니다.")
	@Schema(description = "추가하고자 하는 유저명", example = "tgg")
    private String username;
}
