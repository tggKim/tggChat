package com.tgg.chat.domain.chat.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100)
    private String chatRoomName;
	
}
