package com.tgg.chat.domain.chat.room.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.tgg.chat.domain.chat.room.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.room.enums.ChatRoomUserStatus;
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
            name = "uk_chat_room_user_room_user",
            columnNames = {"chat_room_id", "user_id"}
        )
    }
)
public class ChatRoomUser {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long chatRoomUserId;
	
	// 유저를 구분하기 위한 fk 값
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;
	
	// 채팅방을 구분하기 위한 fk 값
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_room_id")
	private ChatRoom chatRoom;
	
	// 채팅에 참여한 사람 권한 -> OWNER, MEMBER 2가지이며 1대1 채팅에서는 둘 다 MEMBER
	@Enumerated(EnumType.STRING)
	private ChatRoomUserRole chatRoomUserRole;
	
	// 채팅에 참여한 사람 상태 -> ACTIVE, LEFT 2가지
	@Enumerated(EnumType.STRING)
	private ChatRoomUserStatus chatRoomUserStatus;
	
	// 채팅에 참여한 시점 -> 메시지 불러오는 기준
	private LocalDateTime joinedAt;
	
	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;
	
}
