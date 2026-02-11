package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomUserRepository extends JpaRepository<ChatRoomUser, Long> {

    @Query("""
        select cru
        from ChatRoomUser cru
        join fetch cru.user u
        where cru.chatRoom.chatRoomId = :chatRoomId
        and cru.user.userId in :friendIds
    """)
    List<ChatRoomUser> findByChatRoomIdAndFriendIds(Long chatRoomId, List<Long> friendIds);

    List<ChatRoomUser> findByChatRoom(ChatRoom chatRoom);
    
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
            join fetch cru.chatRoom cr
            join fetch cru.user u
            where cr.chatRoomId = :chatRoomId
            and u.userId = :userId
    """)
    Optional<ChatRoomUser> findWithAllDetails(Long chatRoomId, Long userId);
    
    @Query("""
            select cru
            from ChatRoomUser cru
            join fetch cru.user u
            where cru.chatRoom.chatRoomId = :chatRoomId
    """)
    List<ChatRoomUser> findByChatRoomIdWithUser(Long chatRoomId);

}
