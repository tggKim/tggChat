package com.tgg.chat.domain.friend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "친구 생성 요청 DTO")
public class CreateFriendRequestDto {
    @NotNull(message = "userId는 필수입니다.")
    @Positive(message = "userId는 양수여야 합니다.")
	@Schema(description = "추가하고자 하는 유저 ID", example = "1")
    private Long userId;
}
