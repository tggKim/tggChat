package com.tgg.chat.domain.user.entity;

import com.tgg.chat.domain.user.enums.AuthProvider;
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
@Table(uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_user_provider_email",
                columnNames = {"email", "auth_provider"}
        )
})
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, length = 254)
    private String email;

    // 소셜 계정은 비밀번호가 없으므로 NULL 허용
    @Column
    private String password;

    @Column(nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider;

    @Column(nullable = false)
    private Boolean deleted;

    @Column(length = 255)
    private String profileImageKey;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private User(String email, String password, String username, Boolean deleted, AuthProvider authProvider) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.deleted = deleted;
        this.authProvider = authProvider;
    }

    public static User of(String email, String password, String username) {
        return new User(email, password, username, false, AuthProvider.LOCAL);
    }

    public static User of(String email, String password, String username, AuthProvider authProvider) {
        return new User(email, password, username, false, authProvider);
    }

    public void deleteUser() {
        deleted = true;
    }

    public void update(String username) {
        this.username = username;
    }

    public void updateProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }
}
