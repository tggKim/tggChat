package com.tgg.chat.domain.chat.dto.query;

import com.tgg.chat.domain.file.enums.FileCategory;
import lombok.Getter;

@Getter
public class ChatMessageFileRowDto {
    private String fileKey;
    private Integer fileOrder;
    private FileCategory fileCategory;
    private String originalFileName;
    private Long fileSize;
}
