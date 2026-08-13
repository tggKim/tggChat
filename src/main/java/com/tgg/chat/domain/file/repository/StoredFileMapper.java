package com.tgg.chat.domain.file.repository;

import com.tgg.chat.domain.chat.dto.query.ChatMessageFileRowDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StoredFileMapper {
    List<ChatMessageFileRowDto> findOriginalMessageFilesByFileKeys(List<String> storedFileKeys);
}
