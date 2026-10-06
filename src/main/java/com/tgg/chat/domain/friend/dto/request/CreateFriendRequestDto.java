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
    @NotBlank(message = "userId는 필수입니다.")
	@Schema(description = "추가하고자 하는 유저 ID", example = "1")
    private Long userId;
}
