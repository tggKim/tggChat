package com.tgg.chat.domain.chat.room.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.GeneratorType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.tgg.chat.domain.chat.room.enums.ChatRoomType;
import com.tgg.chat.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_chat_room_direct_pair",
            columnNames = {"chat_room_type", "direct_user1_id", "direct_user2_id"}
        )
    },
    indexes = {
    	@Index(name = "idx_chat_room_last_message_at", columnList = "last_message_at")
    }
)
public class ChatRoom {
	
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long chatRoomId;
	
	// 1대1 채팅방인지 단체 채팅방인지 구분을 위한 필드
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChatRoomType chatRoomType;
	
	// 채팅방 이름 필드
	@Column(nullable = false, length = 100)
	private String roomName;
	
	// 1대1 채팅방 중복 생성 방지 필드
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_user1_id")
	private User directUser1;
	
	// 1대1 채팅방 중복 생성 방지 필드
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_user2_id")
	private User directUser2;
	
	// 채팅방 마지막 메시지 필드
	@Column(length = 2000)
	private String lastMessagePreview;
	
	// 채팅방 마지막 메시지 생성 시각
	private LocalDateTime lastMessageAt;
	
	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;

    // 단체 채팅방 생성자
    private ChatRoom(
            ChatRoomType chatRoomType,
            String roomName
    ) {
        this.chatRoomType = chatRoomType;
        this.roomName = roomName;
    }

    // 1대1 채팅방 생성자
    private ChatRoom(
            ChatRoomType chatRoomType,
            String roomName,
            User directUser1,
            User directUser2
    ) {
        this.chatRoomType = chatRoomType;
        this.roomName = roomName;
        this.directUser1 = directUser1;
        this.directUser2 = directUser2;
    }

    public static ChatRoom of(
            ChatRoomType chatRoomType,
            String roomName
    ) {
        return new ChatRoom(chatRoomType, roomName);
    }

    public static ChatRoom of(
            ChatRoomType chatRoomType,
            User directUser1,
            User directUser2
    ) {
        return new ChatRoom(chatRoomType, chatRoomType.name(), directUser1, directUser2);
    }

}
