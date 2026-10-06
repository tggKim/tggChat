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
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_provider_provider_id",
                        columnNames = {"auth_provider", "provider_id"}
                )
        }
)
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    //LOCAL은 필수, 소셜 사용자는 null
    @Column(unique = true, length = 254)
    private String email;

    // 소셜 계정은 비밀번호가 없으므로 NULL 허용
    @Column
    private String password;

    // 화면에 표시되는 이름, 중복 허용
    @Column(nullable = false, length = 50)
    private String username;

    // 사용자에게 공개되는 고유 태그
    @Column(nullable = false, updatable = false, unique = true, length = 8)
    private String userTag;

    // google sub 또는 kakao id
    @Column(name = "provider_id", length = 255)
    private String providerId;

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

    private User(String email, String password, String username, Boolean deleted, String userTag, String providerId, AuthProvider authProvider) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.deleted = deleted;
        this.userTag = userTag;
        this.providerId = providerId;
        this.authProvider = authProvider;
    }

    public static User of(String email, String password, String username, String userTag) {
        return new User(email, password, username, false, userTag, null, AuthProvider.LOCAL);
    }

    public static User of(String email, String password, String username, String userTag, String providerId, AuthProvider authProvider) {
        return new User(email, password, username, false, userTag, providerId,authProvider);
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
