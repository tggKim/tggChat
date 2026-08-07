package com.tgg.chat.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.tgg.chat.domain.user.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>{
    public Optional<User> findByEmail(String email);

    public Optional<User> findByUsername(String username);

    public boolean existsByEmail(String email);

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
}
