package com.tgg.chat.domain.user.repository;

import com.tgg.chat.domain.user.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.tgg.chat.domain.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>{
    public Optional<User> findByEmail(String email);

    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    public boolean existsByUserTag(String userTag);

    public boolean existsByUsername(String username);

    @Query("""
            select distinct receiver.user.userId
            from ChatRoomUser receiver
            where receiver.chatRoom.chatRoomId in (
                select me.chatRoom.chatRoomId
                from ChatRoomUser me
                where me.user.userId = :userId
            )
            and receiver.user.userId <> :userId
            and receiver.user.deleted = false
            and receiver.chatRoomUserStatus = com.tgg.chat.domain.chat.enums.ChatRoomUserStatus.ACTIVE
            """)
    List<Long> findAllInteractingUserIds(Long userId);

    @Query("""
        select u
        from User u
        where u.username = :username
          and u.deleted = false
          and u.userId <> :loginUserId
          and not exists (
              select uf.userFriendId
              from UserFriend uf
              where uf.owner.userId = :loginUserId
                and uf.friend.userId = u.userId
          )
        order by u.userId asc
        """)
    List<User> findFriendCandidatesByUsername(Long loginUserId, String username);
}
