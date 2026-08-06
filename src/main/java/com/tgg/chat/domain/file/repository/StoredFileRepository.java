package com.tgg.chat.domain.file.repository;

import com.tgg.chat.domain.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findAllByFileKey(String fileKey);
}
