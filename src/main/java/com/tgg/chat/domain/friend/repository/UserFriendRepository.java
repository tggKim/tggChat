package com.tgg.chat.domain.friend.repository;

import com.tgg.chat.domain.friend.entity.UserFriend;
import com.tgg.chat.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserFriendRepository extends JpaRepository<UserFriend, Long> {
    public boolean existsByOwner_UserIdAndFriend_UserId(Long ownerId, Long friendId);

    @Query("""
            select count(uf) > 0 
            from UserFriend uf
            where uf.owner.userId = :ownerId
                and uf.friend.userId = :friendId
                and uf.friend.deleted = false
            """)
    boolean existsActiveFriend(Long ownerId, Long friendId);

    @Query("""
            select friend
            from UserFriend uf
            join uf.friend friend
            where uf.owner.userId = :userId
            and uf.friend.userId in :friendIds
            and uf.friend.deleted = false
            """)
    List<User> findActiveFriendsByIds(Long userId, List<Long> friendIds);

    @Query("""
            select friend
            from UserFriend uf
            join uf.friend friend
            where uf.owner.userId = :userId
            and uf.friend.deleted = false
            and not exists (
                select cru
                from ChatRoomUser cru
                where cru.chatRoom.chatRoomId = :chatRoomId
                and cru.user.userId = friend.userId
                and cru.chatRoomUserStatus = com.tgg.chat.domain.chat.enums.ChatRoomUserStatus.ACTIVE
            )
            order by friend.username asc
            """)
    List<User> findInvitableFriends(Long userId, Long chatRoomId);
}
