package com.tgg.chat.domain.user.entity;

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
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true, nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private Boolean deleted;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;


    private User(String email, String password, String username, Boolean deleted) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.deleted = deleted;
    }

    public static User of(String email, String password, String username) {
        return new User(email, password, username, false);
    
    }

    public void deleteUser() {
        deleted = true;
    }

    public void update(String username) {
        this.username = username;
    }

}
