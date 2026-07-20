package com.tgg.chat.domain.chat.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tgg.chat.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	@Query("""
            select cm
            from ChatMessage cm
            join fetch cm.sender
            where cm.chatRoom.chatRoomId = :chatRoomId
            and cm.chatMessageId >= :visibleStartMessageId
            and (
                :offsetMessageId is null
                or
                cm.chatMessageId < :offsetMessageId
            )
            order by cm.chatMessageId desc
            """)
    List<ChatMessage> findVisibleMessages(
            Long chatRoomId,
            Long visibleStartMessageId,
            Long offsetMessageId,
            Limit limit);
}
