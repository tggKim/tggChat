package com.tgg.chat.domain.chat.dto.response;

import com.tgg.chat.common.messaging.event.ChatRoomPreviewUser;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Schema(description = "채팅방 목록 조회 응답 DTO")
public class ChatRoomListResponseDto {

    @Schema(description = "채팅방 ID", example = "1")
    private final Long roomId;

    @Schema(description = "채팅방 타입", example = "GROUP")
    private final ChatRoomType roomType;

    @Schema(description = "채팅방에 설정된 기본 이름, DIRECT 채팅방이면 null", example = "프로젝트 채팅방")
    private final String baseRoomName;

    @Schema(description = "요청 사용자가 설정한 채팅방 이름, 설정하지 않았으면 null", example = "백엔드 스터디")
    private final String customRoomName;

    @Schema(description = "요청 사용자의 채팅방 권한", example = "OWNER")
    private final ChatRoomUserRole myRole;

    @Schema(description = "채팅방에 표시되는 현재 인원수", example = "5")
    private final Long memberCount;

    @Schema(description = "요청 사용자와 삭제된 사용자를 제외한 이름순 미리보기 사용자, 최대 4명")
    private final List<ChatRoomPreviewUser> previewUsers;

    @Schema(description = "최근 표시 가능한 메시지 내용, 메시지가 없으면 null", example = "안녕하세요")
    private final String lastMessagePreview;

    @Schema(description = "최근 표시 가능한 메시지 ID, 메시지가 없으면 null", example = "100")
    private final Long messageId;

    @Schema(description = "채팅방의 최근 활동 시각, 메시지가 없으면 사용자의 최근 참여 시각")
    private final LocalDateTime lastActivityAt;

    @Schema(description = "해당 메시지 ID를 포함한 메시지부터 읽지 않은 상태", example = "95")
    private final Long unreadStartMessageId;

    @Schema(description = "읽지 않은 메시지 개수", example = "5")
    private final Long unreadCount;

    private ChatRoomListResponseDto(
            Long roomId,
            ChatRoomType roomType,
            String baseRoomName,
            String customRoomName,
            ChatRoomUserRole myRole,
            Long memberCount,
            List<ChatRoomPreviewUser> previewUsers,
            String lastMessagePreview,
            Long messageId,
            LocalDateTime lastActivityAt,
            Long unreadStartMessageId,
            Long unreadCount
    ) {
        this.roomId = roomId;
        this.roomType = roomType;
        this.baseRoomName = baseRoomName;
        this.customRoomName = customRoomName;
        this.myRole = myRole;
        this.memberCount = memberCount;
        this.previewUsers = previewUsers;
        this.lastMessagePreview = lastMessagePreview;
        this.messageId = messageId;
        this.lastActivityAt = lastActivityAt;
        this.unreadStartMessageId = unreadStartMessageId;
        this.unreadCount = unreadCount;
    }

    public static ChatRoomListResponseDto of(
            Long roomId,
            ChatRoomType roomType,
            String baseRoomName,
            String customRoomName,
            ChatRoomUserRole myRole,
            Long memberCount,
            List<ChatRoomPreviewUser> previewUsers,
            String lastMessagePreview,
            Long messageId,
            LocalDateTime lastActivityAt,
            Long unreadStartMessageId,
            Long unreadCount
    ) {
        return new ChatRoomListResponseDto(
                roomId,
                roomType,
                baseRoomName,
                customRoomName,
                myRole,
                memberCount,
                previewUsers,
                lastMessagePreview,
                messageId,
                lastActivityAt,
                unreadStartMessageId,
                unreadCount
        );
    }
}
