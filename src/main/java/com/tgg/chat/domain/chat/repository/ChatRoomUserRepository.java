package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomUserRepository extends JpaRepository<ChatRoomUser, Long> {

    @Query("""
        select count(cru) > 0
        from ChatRoomUser cru
        where cru.chatRoom.chatRoomId = :chatRoomId
        and cru.user.userId = :userId
        and cru.user.deleted = false
        and cru.chatRoomUserStatus = com.tgg.chat.domain.chat.enums.ChatRoomUserStatus.ACTIVE
    """)
    boolean existsActiveMember(Long chatRoomId, Long userId);

    @Query("""
        select cru
        from ChatRoomUser cru
        join fetch cru.user u
        where cru.chatRoom.chatRoomId = :chatRoomId
        and cru.user.userId in :friendIds
    """)
    List<ChatRoomUser> findByChatRoomIdAndFriendIds(Long chatRoomId, List<Long> friendIds);
    
    @Query("""
            select cru
            from ChatRoomUser cru
            join fetch cru.chatRoom cr
            join fetch cru.user u
            where cr.chatRoomId = :chatRoomId
            and u.userId = :userId
    """)
    Optional<ChatRoomUser> findByChatRoomIdAndUserIdWithChatRoomAndUser(Long chatRoomId, Long userId);
    
    @Query("""
            select cru
            from ChatRoomUser cru
            join fetch cru.user u
            where cru.chatRoom.chatRoomId = :chatRoomId
            and u.userId = :userId
    """)
    Optional<ChatRoomUser> findByChatRoomIdAndUserIdWithUser(Long chatRoomId, Long userId);

    @Query("""
            select cru
            from ChatRoomUser cru
            where cru.chatRoom.chatRoomId = :chatRoomId
            and cru.user.userId = :userId
    """)
    Optional<ChatRoomUser> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    @Query("""
            select cru
            from ChatRoomUser cru
            join fetch cru.user u
            where cru.chatRoom.chatRoomId = :chatRoomId
            and cru.user.deleted = false
    """)
    List<ChatRoomUser> findByChatRoomIdWithUser(Long chatRoomId);

    @Query("""
        select cru.user.userId
        from ChatRoomUser cru
        where cru.chatRoom.chatRoomId = :chatRoomId
          and cru.chatRoomUserStatus = com.tgg.chat.domain.chat.enums.ChatRoomUserStatus.ACTIVE
          and cru.user.deleted = false
    """)
    List<Long> findActiveUserIds(Long chatRoomId);

    @Query("""
        select cru
        from ChatRoomUser cru
        join fetch cru.user u
        where cru.chatRoom.chatRoomId = :chatRoomId
          and cru.chatRoomUserStatus = com.tgg.chat.domain.chat.enums.ChatRoomUserStatus.ACTIVE
          and cru.user.deleted = false
    """)
    List<ChatRoomUser> findActiveChatRoomUsers(Long chatRoomId);

    @Query("""
        select cru
        from ChatRoomUser cru
        join fetch cru.user u
        where cru.chatRoom.chatRoomId = :chatRoomId
          and cru.chatRoomUserStatus = com.tgg.chat.domain.chat.enums.ChatRoomUserStatus.ACTIVE
          and cru.user.deleted = false
          and cru.user.userId <> :userId
    """)
    List<ChatRoomUser> findOtherActiveChatRoomUsers(Long chatRoomId, Long userId);

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            update ChatRoomUser cru
            set cru.unreadStartMessageId = :newUnreadStartMessageId
            where cru.chatRoomUserId = :chatRoomUserId
            and cru.unreadStartMessageId < :newUnreadStartMessageId
            """)
    int advanceUnreadStartMessageId(Long chatRoomUserId, Long newUnreadStartMessageId);
}
