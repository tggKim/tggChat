package com.tgg.chat.domain.file.dto.internal;

import lombok.Getter;
import org.springframework.core.io.FileSystemResource;

@Getter
public class FindUserImageResult {
    private final FileSystemResource fileSystemResource;
    private final String contentType;
    private final String originalFileName;

    private FindUserImageResult(FileSystemResource fileSystemResource, String contentType, String originalFileName) {
        this.fileSystemResource = fileSystemResource;
        this.contentType = contentType;
        this.originalFileName = originalFileName;
    }

    public static FindUserImageResult of(FileSystemResource fileSystemResource, String contentType, String originalFileName) {
        return new FindUserImageResult(fileSystemResource, contentType, originalFileName);
    }
}
