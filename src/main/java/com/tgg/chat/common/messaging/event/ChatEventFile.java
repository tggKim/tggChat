package com.tgg.chat.common.messaging.event;

import com.tgg.chat.domain.file.enums.FileCategory;
import lombok.Getter;

@Getter
public class ChatEventFile {
    private Integer fileOrder;
    private FileCategory fileCategory;
    private String originalFileName;
    private Long fileSize;

    private ChatEventFile(Integer fileOrder, FileCategory fileCategory, String originalFileName, Long fileSize) {
        this.fileOrder = fileOrder;
        this.fileCategory = fileCategory;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
    }

    public static ChatEventFile of(Integer fileOrder, FileCategory fileCategory, String originalFileName, Long fileSize) {
        return new ChatEventFile(fileOrder, fileCategory, originalFileName, fileSize);
    }
}