package com.tgg.chat.domain.file.dto.internal;

import com.tgg.chat.domain.file.enums.FileCategory;
import lombok.Getter;
import org.springframework.core.io.FileSystemResource;

@Getter
public class FindMessageFileResult {
    private final FileSystemResource fileSystemResource;
    private final String contentType;
    private final String originalFileName;
    private final Long fileSize;
    private final FileCategory fileCategory;

    private FindMessageFileResult(FileSystemResource fileSystemResource, String contentType, String originalFileName, Long fileSize, FileCategory fileCategory) {
        this.fileSystemResource = fileSystemResource;
        this.contentType = contentType;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.fileCategory = fileCategory;
    }

    public static FindMessageFileResult of(FileSystemResource fileSystemResource, String contentType, String originalFileName, Long fileSize, FileCategory fileCategory) {
        return new FindMessageFileResult(fileSystemResource, contentType, originalFileName, fileSize, fileCategory);
    }
}
