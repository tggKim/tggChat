package com.tgg.chat.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "채팅방 초대 요청 DTO")
public class InviteUserRequestDto {

    @Schema(description = "채팅방에 초대할 참여할 userId 목록", example = "{1,2,3}")
    @NotNull(message = "friendIds는 필수입니다.")
    private List<Long> friendIds;

    @Schema(description = "채팅방 id", example = "1")
    @NotNull(message = "chatRoomId는 필수입니다.")
    private Long chatRoomId;

}
