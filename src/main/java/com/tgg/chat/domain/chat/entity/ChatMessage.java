package com.tgg.chat.domain.chat.entity;

import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_message_room_seq", columnNames = {"chat_room_id", "seq"})
        }
)
public class ChatMessage {

    // pk 값
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chatMessageId;

    // 채팅방 fk
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    // 유저 fk
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // 메시지 순번
    @Column(nullable = false)
    private Long seq;

    // 메시지 내용
    @Column(nullable = false, length = 2000)
    private String content;

    // 메시지 타입
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageType chatMessageType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private ChatMessage(
            ChatRoom chatRoom,
            User sender,
            Long seq,
            String content,
            ChatMessageType chatMessageType
    ) {
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.seq = seq;
        this.content = content;
        this.chatMessageType = chatMessageType;
    }

    public static ChatMessage of(
            ChatRoom chatRoom,
            User sender,
            Long seq,
            String content,
            ChatMessageType chatMessageType
    ) {
        return new ChatMessage(chatRoom, sender, seq, content, chatMessageType);
    }

}
