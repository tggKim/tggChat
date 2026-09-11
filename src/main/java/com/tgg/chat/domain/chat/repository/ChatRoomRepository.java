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

    @Query("""
           select
               cr.chatRoomId                AS roomId
           ,   cr.chatRoomType              AS roomType
           ,   cr.roomName                  AS baseRoomName
           ,   cru.customRoomName           AS customRoomName
           ,   cru.chatRoomUserRole         AS myRole
           ,   cru.joinedAt                 AS joinedAt
           ,   cru.unreadStartMessageId     AS unreadStartMessageId
           from ChatRoomUser cru
           join cru.chatRoom cr
           where cru.user.userId = :userId
           and cru.chatRoomUserStatus = com.tgg.chat.domain.chat.enums.ChatRoomUserStatus.ACTIVE
            """)
    List<ChatRoomListBaseRowDto> findActiveChatRoomsByUserId(Long userId);

//    List<ChatRoomMemberCountRowDto> findMemberCountsByChatRoomIds(List<Long> roomIds);
//
//    List<ChatRoomPreviewUserRowDto> findPreviewUsersByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);
//
//    List<ChatRoomLatestMessageRowDto> findLatestVisibleMessagesByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);
//
//    List<ChatRoomUnreadCountRowDto> findUnreadMessageCountsByUserIdAndChatRoomIds(Long userId, List<Long> roomIds);
}
