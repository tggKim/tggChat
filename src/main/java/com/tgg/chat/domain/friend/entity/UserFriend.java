package com.tgg.chat.domain.friend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.GeneratorType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.tgg.chat.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
	uniqueConstraints = @UniqueConstraint(
		name = "uk_user_friends_owner_friend",
		columnNames = {"owner_id", "friend_id"}
	)
)
public class UserFriend {
	
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long userFriendId;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "friend_id", nullable = false)
	private User friend;
	
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    private UserFriend(User owner, User friend) {
    	this.owner = owner;
    	this.friend = friend;
    }
    
    public static UserFriend of(User owner, User friend) {
    	return new UserFriend(owner, friend);
    }

}
