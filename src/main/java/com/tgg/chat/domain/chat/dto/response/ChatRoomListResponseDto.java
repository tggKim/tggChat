package com.tgg.chat.domain.chat.dto.response;

import com.tgg.chat.domain.chat.dto.query.ChatRoomListRowDto;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Schema(description = "채팅방 목록 조회 응답 DTO")
public class ChatRoomListResponseDto {

    @Schema(description = "유저 ID", example = "1")
    private final Long userId;
    
    @Schema(description = "유저명", example = "tgg")
    private final String username;
    
    @Schema(description = "채팅방 목록")
    private final List<ChatRoomListItemReseponseDto> chatRooms;

    private ChatRoomListResponseDto(
    		Long userId,
    		String username,
    		List<ChatRoomListItemReseponseDto> chatRooms
    ) {
        this.userId = userId;
        this.username = username;
        this.chatRooms = chatRooms;
    }

    public static ChatRoomListResponseDto of(
		Long userId,
		String username,
		List<ChatRoomListItemReseponseDto> chatRooms
    ) {
        return new ChatRoomListResponseDto(userId, username, chatRooms);
    }
}
