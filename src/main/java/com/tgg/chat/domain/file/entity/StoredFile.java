package com.tgg.chat.domain.file.entity;

import com.tgg.chat.domain.file.enums.FileCategory;
import com.tgg.chat.domain.file.enums.StoredFileVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        indexes = {
                @Index(
                        name = "idx_file_key_file_order",
                        columnList = "file_key,file_order"
                )
        }
)
public class StoredFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long storedFileId;

    @Column(nullable = false, updatable = false)
    private String fileKey;

    @Column(nullable = false, updatable = false)
    private String storedFileName;

    @Column(nullable = false, updatable = false)
    private String originalFileName;

    @Column(nullable = false, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private Long fileSize;

    @Column(nullable = false, updatable = false)
    private Integer fileOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private StoredFileVariant storedFileVariant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FileCategory fileCategory;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private StoredFile(
            String fileKey,
            String storedFileName,
            String originalFileName,
            String contentType,
            Long fileSize,
            Integer fileOrder,
            StoredFileVariant storedFileVariant,
            FileCategory fileCategory
    ) {
        this.fileKey = fileKey;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileOrder = fileOrder;
        this.storedFileVariant = storedFileVariant;
        this.fileCategory = fileCategory;
    }

    public static StoredFile of(
            String fileKey,
            String storedFileName,
            String originalFileName,
            String contentType,
            Long fileSize,
            Integer fileOrder,
            StoredFileVariant storedFileVariant,
            FileCategory fileCategory
    ) {
        return new StoredFile(
                fileKey,
                storedFileName,
                originalFileName,
                contentType,
                fileSize,
                fileOrder,
                storedFileVariant,
                fileCategory
        );
    }
}
