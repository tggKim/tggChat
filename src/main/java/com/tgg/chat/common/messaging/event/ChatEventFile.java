package com.tgg.chat.common.messaging.event;

import lombok.Getter;

@Getter
public class ChatEventFile {
    private String fileType;
    private String fileKey;
    private String fileName;

    private ChatEventFile(String fileType, String fileKey, String fileName) {
        this.fileType = fileType;
        this.fileKey = fileKey;
        this.fileName = fileName;
    }

    public static ChatEventFile of(String fileType, String fileKey, String fileName) {
        return new ChatEventFile(fileType, fileKey, fileName);
    }
}
