package com.tgg.chat.domain.friend.repository;

import com.tgg.chat.domain.friend.entity.UserFriend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFriendRepository extends JpaRepository<UserFriend, Long> {
    public boolean existsByOwner_UserIdAndFriend_UserId(Long ownerId, Long friendId);
}
