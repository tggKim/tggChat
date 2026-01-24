package com.tgg.chat.domain.chat.room.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "단체 채팅방 생성 요청 DTO")
public class CreateGroupChatRoomRequestDto {

    @Schema(description = "채팅방 생성시 참여할 userId 목록", example = "{1,2,3}")
    @NotNull(message = "friendIds는 필수입니다.")
    private List<Long> friendIds;
    
    @Schema(description = "채팅방 이름", example = "그룹 채팅방1")
    @NotBlank(message = "chatRoomName은 필수이며 공백일 수 없습니다.")
    private String chatRoomName;
	
}
