package com.tgg.chat.domain.chat.room.repository;

import com.tgg.chat.domain.chat.room.entity.ChatRoomUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

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

}
