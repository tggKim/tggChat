package com.tgg.chat.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "사용자별 커스텀 채팅방 이름 수정 요청 DTO")
public class UpdateCustomRoomNameRequestDto {
    @NotBlank(message = "채팅방 이름은 필수입니다.")
    @Size(max = 100, message = "채팅방 이름은 최대 100자까지 입력할 수 있습니다.")
    @Schema(description = "수정할 사용자별 커스텀 채팅방 이름", example = "수정한 이름")
    private String customRoomName;
}
