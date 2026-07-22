package com.tgg.chat.domain.friend.repository;

import com.tgg.chat.domain.friend.entity.UserFriend;
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
            select count(uf)
            from UserFriend uf
            where uf.owner.userId = :userId
            and uf.friend.userId in :friendIds
            and uf.friend.deleted = false
            """)
    Long countActiveFriendsByIds(Long userId, List<Long> friendIds);
}
