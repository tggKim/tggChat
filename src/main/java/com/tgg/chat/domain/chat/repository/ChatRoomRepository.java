package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.dto.query.*;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    @Query("""
              select cr
              from ChatRoom cr
              where cr.chatRoomType = com.tgg.chat.domain.chat.enums.ChatRoomType.DIRECT
                and cr.directUser1.userId = :directUser1Id
                and cr.directUser2.userId = :directUser2Id
            """)
    Optional<ChatRoom> findDirectChatRoom(Long directUser1Id, Long directUser2Id);

    @Query(
            value = """
                SELECT
                    cr.chat_room_id             AS roomId,
                    cr.chat_room_type           AS roomType,
                    cr.room_name                AS baseRoomName,
                    cru.custom_room_name        AS customRoomName,
                    cru.chat_room_user_role     AS myRole,
                    cru.joined_at               AS joinedAt,
                    cru.unread_start_message_id AS unreadStartMessageId
                FROM chat_room_user cru
                INNER JOIN chat_room cr
                    ON cru.chat_room_id = cr.chat_room_id
                WHERE cru.user_id = :userId
                AND cru.chat_room_user_status = 'ACTIVE'
                """,
            nativeQuery = true
    )
    List<ChatRoomListBaseRowDto> findActiveChatRoomsByUserId(Long userId);

    @Query(
            value = """
                SELECT
                    cru.chat_room_id AS roomId,
                    COUNT(*)         AS memberCount
                FROM chat_room_user cru
                INNER JOIN user u
                    ON cru.user_id = u.user_id
                INNER JOIN chat_room cr
                    ON cru.chat_room_id = cr.chat_room_id
                WHERE cru.chat_room_id IN (:roomIds)
                  AND (
                      cru.chat_room_user_status = 'ACTIVE'
                      OR cr.chat_room_type = 'DIRECT'
                  )
                  AND u.deleted = false
                GROUP BY cru.chat_room_id
                """,
            nativeQuery = true
    )
    List<ChatRoomMemberCountRowDto> findMemberCountsByChatRoomIds(List<Long> roomIds);

    @Query(
            value = """
                SELECT
                    ranked.chat_room_id      AS roomId,
                    ranked.user_id           AS userId,
                    ranked.username          AS username,
                    ranked.profile_image_key AS profileImageKey
                FROM (
                    SELECT
                        cru.chat_room_id,
                        cru.user_id,
                        u.username,
                        u.profile_image_key,
                        ROW_NUMBER() OVER (
                            PARTITION BY cru.chat_room_id
                            ORDER BY u.username
                        ) AS preview_order
                    FROM chat_room_user cru
                    INNER JOIN chat_room cr
                        ON cru.chat_room_id = cr.chat_room_id
                    INNER JOIN user u
                        ON cru.user_id = u.user_id
                    WHERE cru.chat_room_id IN (:roomIds)
                      AND (
                          cr.chat_room_type = 'DIRECT'
                          OR cru.chat_room_user_status = 'ACTIVE'
                      )
                      AND cru.user_id <> :userId
                      AND u.deleted = false
                ) ranked
                WHERE ranked.preview_order <= 4
                ORDER BY ranked.chat_room_id, ranked.preview_order
                """,
            nativeQuery = true
    )
    List<ChatRoomPreviewUserRowDto> findPreviewUsersByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);
//
//    List<ChatRoomLatestMessageRowDto> findLatestVisibleMessagesByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);
//
//    List<ChatRoomUnreadCountRowDto> findUnreadMessageCountsByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);
}
