package com.tgg.chat.domain.file.dto.internal;

import lombok.Getter;
import org.springframework.core.io.FileSystemResource;

@Getter
public class FindUserImageResult {
    private final FileSystemResource fileSystemResource;
    private final String contentType;

    private FindUserImageResult(FileSystemResource fileSystemResource, String contentType) {
        this.fileSystemResource = fileSystemResource;
        this.contentType = contentType;
    }

    public static FindUserImageResult of(FileSystemResource fileSystemResource, String contentType) {
        return new FindUserImageResult(fileSystemResource, contentType);
    }
}
